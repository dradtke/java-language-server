import * as Parser from 'web-tree-sitter';

export function colorJava(root: Parser.SyntaxNode, visibleRanges: { start: number, end: number }[]): { [scope: string]: Parser.SyntaxNode[] } {
	const colors: { [scope: string]: Parser.SyntaxNode[] } = {
		'entity.name.type': [],
		'entity.name.function': [],
		'variable': [],
	};
	class Scope {
		private vars = new Set<string>();
		parent: Scope | null;
		local: boolean;

		constructor(parent: Scope | null, local: boolean) {
			this.parent = parent;
			this.local = local;
		}

		declare(id: string) {
			if (this.isRoot()) return;
			this.vars.add(id);
		}

		isLocal(id: string): boolean {
			if (this.vars.has(id)) return this.local;
			if (this.parent) return this.parent.isLocal(id);
			return false;
		}

		isRoot(): boolean {
			return this.parent == null;
		}
	}
	let visitedChildren = false;
	let cursor = root.walk();
	let scope = new Scope(null, false);
	let parents = [cursor.nodeType];
	while (true) {
		// Advance cursor
		if (visitedChildren) {
			if (cursor.gotoNextSibling()) {
				visitedChildren = false;
			} else if (cursor.gotoParent()) {
				parents.pop();
				if (createsScope(cursor.nodeType)) {
					scope = scope.parent;
				}
				visitedChildren = true;
			} else {
				break;
			}
		} else {
			const parent = cursor.nodeType;
			if (cursor.gotoFirstChild()) {
				if (createsScope(parent)) {
					scope = new Scope(scope, createsLocalScope(parent));
				}
				parents.push(parent);
				visitedChildren = false;
			} else {
				visitedChildren = true;
			}
		}
		// Color tokens
		switch (cursor.nodeType) {
			case 'import_declaration':
			case 'package_declaration':
				// Skip package and import declarations
				visitedChildren = true;
				break;
			case 'class_declaration':
				// Skip classes that are not visible
				if (!isVisible(cursor, visibleRanges)) {
					visitedChildren = true;
				}
				break;
			case 'identifier':
				const parent = parents[parents.length-1];
				// If this identifier is part of a variable declaration, declare it in the current scope
				const isParam = parent == 'inferred_parameters' ||
					parent == 'lambda_expression' && cursor.currentFieldName() == 'parameters' ||
					parent == 'formal_parameter' && cursor.currentFieldName() == 'name' ||
					parent == 'variable_declarator' && cursor.currentFieldName() == 'name' ||
					parent == 'enhanced_for_statement' && cursor.currentFieldName() == 'name' ||
					parent == 'catch_formal_parameter' && cursor.currentFieldName() == 'name' ||
					parent == 'resource_specification' && cursor.currentFieldName() == 'name';
				if (isParam) {
					scope.declare(cursor.currentNode().text);
				}
				// If this identifier is part of a selection or invocation, deal with it when we scan the parent
				const skip = parent == 'method_invocation' && cursor.currentFieldName() == 'name' || 
					parent == 'scoped_identifier' || 
					parent == 'field_access' ||
					parent == 'method_reference';
				if (skip) {
					break;
				}
				// If this identifier is the name of a class declaration, or part of a type parameter
				const isTypeName = parent == 'class_declaration' && cursor.currentFieldName() == 'name'
					|| parent == 'type_parameter';
				if (isTypeName) {
					colors['entity.name.type'].push(cursor.currentNode());
					break;
				}
				// If this identifier is the name in a method or constructor declaration
				const isMethodName = parent == 'method_declaration' && cursor.currentFieldName() == 'name' ||
					parent == 'constructor_declarator';
				if (isMethodName) {
					colors['entity.name.function'].push(cursor.currentNode());
					break;
				}
				// If this identifier is a reference to a non-local variable, color it
				const reference = cursor.currentNode().text;
				if (looksLikeVar(reference) && !scope.isLocal(reference)) {
					colors['variable'].push(cursor.currentNode());
				}
				break;
			case 'type_identifier':
				if (cursor.currentNode().text != 'var') {
					colors['entity.name.type'].push(cursor.currentNode()); // TODO use startPosition() / endPosition() instead of currentNode()
				}
				break;
			case 'field_access':
			case 'scoped_identifier':
				const select = cursor.currentNode().namedChild(1);
				if (looksLikeVar(select.text)) {
					colors['variable'].push(select);
				}
				break;
		}
	}
	cursor.delete();
	return colors;
}
// function looksLikeType(id: string) {
// 	// ''
// 	if (id.length == 0) return false;
// 	// 'FOO'
// 	if (id.toUpperCase() == id) return false;
// 	// 'Foo'
// 	if (id[0].toUpperCase() == id[0]) return true;
// 	return false;
// }
function looksLikeVar(id: string) {
	return id[0].toLowerCase() == id[0];
}
function isVisible(x: Parser.TreeCursor, visibleRanges: { start: number, end: number }[]) {
	for (const { start, end } of visibleRanges) {
		const overlap = x.startPosition.row <= end + 1 && start - 1 <= x.endPosition.row;
		if (overlap) return true;
	}
	return false;
}
function createsScope(nodeType: string) {
	switch (nodeType) {
		case 'class_declaration':
		case 'method_declaration':
		case 'constructor_declaration':
		case 'block':
		case 'lambda_expression':
		case 'enhanced_for_statement':
		case 'catch_clause':
		case 'try_with_resources_statement':
			return true;
		default:
			return false;
	}
}
function createsLocalScope(nodeType: string) {
	switch (nodeType) {
		case 'class_declaration':
			return false;
		case 'method_declaration':
		case 'constructor_declaration':
		case 'block':
		case 'lambda_expression':
		case 'enhanced_for_statement':
		case 'catch_clause':
		case 'try_with_resources_statement':
			return true;
		default:
			throw new Error(nodeType + ' does not create a scope');
	}
}
