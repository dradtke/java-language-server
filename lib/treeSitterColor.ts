import * as Parser from 'web-tree-sitter';

export type Range = {start: Parser.Point, end: Parser.Point}

export function colorJava(root: Parser.SyntaxNode, visibleRanges: { start: number, end: number }[]): Map<string, Range[]> {
	const functions: Range[] = []
	const types: Range[] = []
	let visitedChildren = false;
	let cursor = root.walk();
	let parents = [cursor.nodeType];
	while (true) {
		// Advance cursor
		if (visitedChildren) {
			if (cursor.gotoNextSibling()) {
				visitedChildren = false;
			} else if (cursor.gotoParent()) {
				parents.pop();
				visitedChildren = true;
				continue;
			} else {
				break;
			}
		} else {
			const parent = cursor.nodeType;
			if (cursor.gotoFirstChild()) {
				parents.push(parent);
				visitedChildren = false;
			} else {
				visitedChildren = true;
				continue;
			}
		}
		// Skip nodes that are not visible
		if (!isVisible(cursor, visibleRanges)) {
			visitedChildren = true;
			continue;
		}
		// Color tokens
		switch (cursor.nodeType) {
			case 'import_declaration':
			case 'package_declaration':
				// Skip package and import declarations
				visitedChildren = true;
				break;
			case 'identifier':
				const parent = parents[parents.length-1];
				// If this identifier is the name of a class declaration, or part of a type parameter
				const isTypeName = parent == 'class_declaration' && cursor.currentFieldName() == 'name'
					|| parent == 'type_parameter';
				if (isTypeName) {
					types.push({start: cursor.startPosition, end: cursor.endPosition})
					break;
				}
				// If this identifier is the name in a method or constructor declaration
				const isMethodName = parent == 'method_declaration' && cursor.currentFieldName() == 'name' ||
					parent == 'constructor_declarator';
				if (isMethodName) {
					functions.push({start: cursor.startPosition, end: cursor.endPosition})
					break;
				}
				break;
			case 'type_identifier':
				if (cursor.currentNode().text != 'var') {
					types.push({start: cursor.startPosition, end: cursor.endPosition})
				}
				break;
		}
	}
	cursor.delete();
	return new Map([
		['entity.name.function', functions],
		['entity.name.type', types],
	])
}
function isVisible(x: Parser.TreeCursor, visibleRanges: { start: number, end: number }[]) {
	for (const { start, end } of visibleRanges) {
		const overlap = x.startPosition.row <= end + 1 && start - 1 <= x.endPosition.row;
		if (overlap) return true;
	}
	return false;
}