import * as Parser from 'web-tree-sitter';

export function colorJava(root: Parser.SyntaxNode, visibleRanges: {start: number, end: number}[]): {[scope: string]: Parser.SyntaxNode[]} {
	const colors: {[scope: string]: Parser.SyntaxNode[]} = {
		'entity.name.type': [],
		'entity.name.function': [],
		'variable': [],
	};
	class Scope {
		private vars = new Map<string, {local:boolean}>();
		private parent: Scope|null;

		constructor(parent: Scope|null) {
			this.parent = parent;
		}

		declareLocal(id: string) {
			if (this.isRoot()) return;
			this.vars.set(id, {local:true});
		}

		declareField(id: string) {
			if (this.isRoot()) return;
			this.vars.set(id, {local:false});
		}

		isLocal(id: string): boolean {
			if (this.vars.has(id)) return this.vars.get(id)!.local;
			if (this.parent) return this.parent.isLocal(id);
			return false;
		}

		isRoot(): boolean {
			return this.parent == null;
		}
	}
	function scanProgram(x: Parser.SyntaxNode) {
		const scope = new Scope(null);
		for (const child of x.namedChildren) {
			if (!isVisible(child, visibleRanges)) continue;
			switch (child.type) {
				case 'package_declaration':
				case 'import_declaration':
					break;
				case 'class_declaration':
					scanTopLevelClass(child, scope);
					break;
				default:
					scan(child, scope);
			}
		}
	}
	function scanTopLevelClass(x: Parser.SyntaxNode, scope: Scope) {
		for (const child of x.children) {
			if (!isVisible(child, visibleRanges)) continue;
			scan(x, scope);
		}
	}
	function scan(x: Parser.SyntaxNode, scope: Scope) {
		switch (x.type) {
			case 'ERROR':
				return;
			case 'constructor_declaration':
			case 'method_declaration':
			case 'class_body':
			case 'method_body':
			case 'block':
				scope = new Scope(scope);
				break;
			case 'lambda_expression':
				scope = new Scope(scope);
				for (const child of x.namedChildren) {
					if (child.equals(x.lastNamedChild)) {
						scan(child, scope);
					} else {
						scanLambdaArgument(child, scope);
					}
				}
				return;
			case 'field_declaration':
				scanFieldDeclaration(x, scope);
				return;
			case 'variable_declarator_id':
				// Anything which isn't a field, must be a local
				scope.declareLocal(x.text);
				return; 
			case 'class_literal':
				colors['entity.name.type'].push(x.firstChild);
				return;
			case 'type_identifier':
				if (x.text != 'var') {
					colors['entity.name.type'].push(x);
				}
				break;
			case 'scoped_identifier':
				if (!looksLikeType(x.lastNamedChild!.text)) {
					colors['variable'].push(x.lastNamedChild!);
				}
				scan(x.firstNamedChild!, scope);
				break;
			case 'field_access':
				colors['variable'].push(x.lastNamedChild);
				for (const child of x.namedChildren) {
					if (!child.equals(x.lastNamedChild)) {
						scan(child, scope);
					}
				}
				return;
			case 'method_invocation':
			case 'method_reference':
				const select = [];
				for (const child of x.namedChildren) {
					switch (child.type) {
						case 'type_argument':
						case 'argument_list':
							scan(child, scope);
							break;
						default:
							select.push(child);
					}
				}
				if (select.length > 1) {
					// First id can be anything
					scan(select[0], scope);
					// Middle ids are usually fields
					for (var i = 1; i < select.length - 1; i++) {
						if (select[i].type == 'identifier' && !looksLikeType(select[i].text)) {
							colors['variable'].push(select[i]);
						}
					}
					// Last id is method name
				}
				return;
			case 'identifier':
				switch (x.parent!.type) {
					case 'class_declaration':
					case 'type_parameter':
						colors['entity.name.type'].push(x);
						break;
					case 'constructor_declarator':
					case 'method_declaration':
						colors['entity.name.function'].push(x);
						break;
					default:
						if (!looksLikeType(x.text) && !scope.isLocal(x.text)) {
							colors['variable'].push(x);
						}
				}
				break;
			// TODO stop early
		}
		for (const child of x.namedChildren) {
			scan(child, scope)
		}
	}
	function scanLambdaArgument(x: Parser.SyntaxNode, scope: Scope) {
		switch (x.type) {
			case 'identifier':
				scope.declareLocal(x.text);
				break;
			case 'type_identifier':
				if (x.text != 'var') {
					colors['entity.name.type'].push(x);
				}
				break;
			default:
				for (const child of x.namedChildren) {
					scanLambdaArgument(child, scope);
				}
		}
	}
	function scanFieldDeclaration(x: Parser.SyntaxNode, scope: Scope) {
		switch (x.type) {
			case 'variable_declarator_id':
				scope.declareField(x.text);
				colors['variable'].push(x);
				break;
			case 'type_identifier':
				if (x.text != 'var') {
					colors['entity.name.type'].push(x);
				}
				break;
			default:
				for (const child of x.namedChildren) {
					scanFieldDeclaration(child, scope)
				}
		}
	}
	function looksLikeType(id: string) {
		// ''
		if (id.length == 0) return false;
		// 'FOO'
		if (id.toUpperCase() == id) return false;
		// 'Foo'
		if (id[0].toUpperCase() == id[0]) return true;
		return false;
	}
	scanProgram(root);
	return colors;
}

function isVisible(x: Parser.SyntaxNode, visibleRanges: {start: number, end: number}[]) {
	for (const {start, end} of visibleRanges) {
		const overlap = x.startPosition.row <= end + 1 && start - 1 <= x.endPosition.row;
		if (overlap) return true;
	}
	return false;
}
