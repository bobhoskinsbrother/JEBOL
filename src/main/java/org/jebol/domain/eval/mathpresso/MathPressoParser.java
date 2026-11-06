package org.jebol.domain.eval.mathpresso;

import org.jebol.domain.eval.mathpresso.MathPressoNode.Binary;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Block;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Immediate;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Unary;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Variable;
import org.jebol.domain.eval.mathpresso.MathPressoNode.VariableDeclaration;
import org.jebol.domain.eval.mathpresso.MathPressoScope.Resolved;
import org.jebol.domain.eval.mathpresso.MathPressoToken.Type;

import java.util.Optional;

final class MathPressoParser {

    private final MathPressoTokenizer tokenizer;
    private final MathPressoReporter reporter;
    private final MathPressoScope rootScope;
    private MathPressoScope currentScope;
    private int slotsUsed;

    MathPressoParser(MathPressoTokenizer tokenizer, MathPressoReporter reporter, MathPressoScope rootScope) {
        this.tokenizer = tokenizer;
        this.reporter = reporter;
        this.rootScope = rootScope;
        this.currentScope = rootScope;
    }

    int slotsUsed() {
        return slotsUsed;
    }

    Block parseProgram() {
        Block program = new Block(true);
        while (tokenizer.peek().type() != Type.END) {
            parseStatement(program, true);
        }
        if (program.size() == 0) {
            throw reporter.refuseSilently();
        }
        return program;
    }

    private void parseStatement(Block block, boolean mayDeclare) {
        MathPressoToken token = tokenizer.peek();
        if (token.type() == Type.SEMICOLON) {
            tokenizer.consume();
            return;
        }
        if (token.type() == Type.LEFT_CURLY) {
            if (!mayDeclare) {
                throw reporter.refuse("Cannot declare a new block-scope here.");
            }
            Block nested = new Block(false);
            block.append(nested);
            MathPressoScope outer = currentScope;
            currentScope = new MathPressoScope(outer, MathPressoScope.Type.NESTED);
            try {
                parseBlockOrStatement(nested);
            } finally {
                currentScope = outer;
            }
            return;
        }
        if (token.type() == Type.VAR) {
            if (!mayDeclare) {
                throw reporter.refuse("Cannot declare a new variable here.");
            }
            parseVariableDeclaration(block);
            return;
        }
        block.append(parseExpression(false));
        MathPressoToken after = tokenizer.peek();
        if (after.type() == Type.SEMICOLON) {
            tokenizer.consume();
            return;
        }
        if (after.type() == Type.END) {
            return;
        }
        throw reporter.refuse("Expected a ';' after an expression.");
    }

    private void parseBlockOrStatement(Block block) {
        MathPressoToken token = tokenizer.next();
        block.setPosition(token.position());
        if (token.type() == Type.LEFT_CURLY) {
            while (true) {
                if (tokenizer.peek().type() == Type.RIGHT_CURLY) {
                    tokenizer.consume();
                    return;
                }
                parseStatement(block, true);
            }
        }
        parseStatement(block, false);
    }

    private void parseVariableDeclaration(Block block) {
        MathPressoToken token = tokenizer.next();
        int position = token.position();
        boolean isFirst = true;
        MathPressoScope scope = currentScope;
        while (true) {
            token = tokenizer.next();
            if (token.type() != Type.SYMBOL) {
                throw reporter.refuse(isFirst
                        ? "Expected a variable name after 'var' keyword."
                        : "Expected a variable name after colon ','.");
            }
            if (!isFirst) {
                position = token.position();
            }
            String name = tokenizer.textAt(token);
            Optional<Resolved> existing = scope.resolve(name);
            if (existing.isPresent()) {
                MathPressoSymbol found = existing.get().symbol();
                if (found.isIntrinsic() || existing.get().scope() == scope) {
                    throw reporter.refuse("Attempt to redefine '" + found.name() + "'.");
                }
                if (found.declaration() != null) {
                    reporter.warn("Variable '" + found.name() + "' shadows a variable declared at "
                            + reporter.locate(found.declaration().position()) + ".");
                } else {
                    reporter.warn("Variable '" + found.name() + "' shadows a variable of the same name.");
                }
            }
            MathPressoSymbol symbol = MathPressoSymbol.declaredVariable(name);
            scope.put(symbol);
            VariableDeclaration declaration = new VariableDeclaration(symbol);
            declaration.setPosition(position);
            symbol.setSlot(slotsUsed++);
            symbol.setDeclaration(declaration);
            token = tokenizer.next();
            if (token.type() == Type.ASSIGN) {
                declaration.setChildAt(0, parseExpression(false));
                symbol.countWrite();
                token = tokenizer.next();
            }
            symbol.markDeclared();
            if (token.type() == Type.COMMA || token.type() == Type.SEMICOLON || token.type() == Type.END) {
                block.append(declaration);
                if (token.type() != Type.COMMA) {
                    return;
                }
            } else {
                throw reporter.refuse("Unexpected token.");
            }
            isFirst = false;
        }
    }

    private MathPressoNode parseExpression(boolean isNestedAtStart) {
        boolean isNested = isNestedAtStart;
        MathPressoScope scope = currentScope;
        Binary operatorNode = null;
        MathPressoNode termNode = null;
        while (true) {
            Unary unary = null;
            boolean readingTerm = true;
            while (readingTerm) {
                MathPressoToken token = tokenizer.next();
                MathPressoNode reached;
                switch (token.type()) {
                    case SYMBOL -> reached = parseSymbol(token, scope);
                    case NUMBER -> {
                        reached = new Immediate(Double.doubleToRawLongBits(token.value()));
                        reached.setPosition(token.position());
                    }
                    case COMMA, COLON, SEMICOLON, RIGHT_PAREN -> throw reporter.refuse("Expected an expression.");
                    case LEFT_PAREN -> {
                        reached = parseExpression(true);
                        if (tokenizer.next().type() != Type.RIGHT_PAREN) {
                            throw reporter.refuse("Expected a ')' token.");
                        }
                    }
                    case ADD, SUB, NOT -> {
                        Unary operator = new Unary(switch (token.type()) {
                            case ADD -> MathPressoOperator.NONE;
                            case SUB -> MathPressoOperator.NEG;
                            default -> MathPressoOperator.NOT;
                        });
                        operator.setPosition(token.position());
                        if (unary == null) {
                            termNode = operator;
                        } else {
                            unary.setChildAt(0, operator);
                        }
                        isNested = true;
                        unary = operator;
                        continue;
                    }
                    case END -> throw reporter.refuse("Unexpected end of the program.");
                    default -> throw reporter.refuse("Unexpected token.");
                }
                if (unary == null) {
                    termNode = reached;
                } else {
                    unary.setChildAt(0, reached);
                }
                readingTerm = false;
            }

            MathPressoToken token = tokenizer.next();
            MathPressoOperator operator;
            switch (token.type()) {
                case COMMA, COLON, SEMICOLON, RIGHT_PAREN, END -> {
                    tokenizer.putBack(token);
                    if (operatorNode != null) {
                        operatorNode.setChildAt(1, termNode);
                        while (operatorNode.hasParent()) {
                            operatorNode = (Binary) operatorNode.parent();
                        }
                        termNode = operatorNode;
                    }
                    return termNode;
                }
                case ASSIGN -> {
                    if (!(termNode instanceof Variable target)) {
                        throw reporter.refuse("Can't assign to a non-variable.");
                    }
                    MathPressoSymbol symbol = target.symbol();
                    if (symbol.isReadOnly()) {
                        throw reporter.refuse("Can't assign to a read-only variable '" + symbol.name() + "'.");
                    }
                    if (isNested) {
                        throw reporter.refuse("Invalid assignment inside an expression.");
                    }
                    symbol.countWrite();
                    operator = MathPressoOperator.ASSIGN;
                }
                case EQ -> operator = MathPressoOperator.EQ;
                case NE -> operator = MathPressoOperator.NE;
                case GT -> operator = MathPressoOperator.GT;
                case GE -> operator = MathPressoOperator.GE;
                case LT -> operator = MathPressoOperator.LT;
                case LE -> operator = MathPressoOperator.LE;
                case ADD -> operator = MathPressoOperator.ADD;
                case SUB -> operator = MathPressoOperator.SUB;
                case MUL -> operator = MathPressoOperator.MUL;
                case DIV -> operator = MathPressoOperator.DIV;
                case MOD -> operator = MathPressoOperator.MOD;
                default -> throw reporter.refuse("Unexpected token.");
            }
            Binary made = new Binary(operator);
            made.setPosition(token.position());
            if (operatorNode == null) {
                made.setChildAt(0, termNode);
                operatorNode = made;
                continue;
            }
            int previousPrecedence = operatorNode.operator().precedence();
            int nextPrecedence = operator.precedence();
            if (previousPrecedence > nextPrecedence) {
                operatorNode.setChildAt(1, made);
                made.setChildAt(0, termNode);
                operatorNode = made;
                continue;
            }
            operatorNode.setChildAt(1, termNode);
            while (operatorNode.hasParent()) {
                if (operatorNode.operator().yieldsTo(nextPrecedence)) {
                    break;
                }
                operatorNode = (Binary) operatorNode.parent();
            }
            if (!operatorNode.hasParent() && !operatorNode.operator().yieldsTo(nextPrecedence)) {
                made.setChildAt(0, operatorNode);
            } else {
                MathPressoNode right = operatorNode.unlinkChildAt(1);
                operatorNode.setChildAt(1, made);
                made.setChildAt(0, right);
            }
            isNested = true;
            operatorNode = made;
        }
    }

    private MathPressoNode parseSymbol(MathPressoToken token, MathPressoScope scope) {
        String name = tokenizer.textAt(token);
        Resolved resolved = scope.resolve(name)
                .orElseThrow(() -> reporter.refuse("Unresolved symbol " + name + "."));
        MathPressoSymbol symbol = resolved.symbol();
        if (symbol.isIntrinsic()) {
            tokenizer.putBack(token);
            return parseCall();
        }
        if (!symbol.isDeclared()) {
            throw reporter.refuse("Can't use variable '" + symbol.name() + "' that is being declared.");
        }
        if (resolved.scope().isGlobal()) {
            symbol = symbol.shadow();
            symbol.setSlot(slotsUsed++);
            rootScope.put(symbol);
        }
        Variable variable = new Variable(symbol);
        variable.setPosition(token.position());
        return variable;
    }

    private MathPressoNode parseCall() {
        MathPressoToken token = tokenizer.next();
        int position = token.position();
        String name = tokenizer.textAt(token);
        MathPressoSymbol symbol = currentScope.resolve(name)
                .orElseThrow(() -> reporter.refuse("Unresolved symbol " + name + "."))
                .symbol();
        if (tokenizer.next().type() != Type.LEFT_PAREN) {
            throw reporter.refuse("Expected a '(' token after a function name.");
        }
        MathPressoNode[] arguments = new MathPressoNode[2];
        int count = 0;
        MathPressoToken after = tokenizer.peek();
        if (after.type() != Type.RIGHT_PAREN) {
            while (true) {
                MathPressoNode argument = parseExpression(true);
                if (count < arguments.length) {
                    arguments[count] = argument;
                }
                count++;
                after = tokenizer.peek();
                if (after.type() == Type.RIGHT_PAREN) {
                    break;
                }
                if (after.type() != Type.COMMA) {
                    throw reporter.refuse("Expected either ',' or ')' token.");
                }
                tokenizer.consume();
            }
        }
        tokenizer.consume();
        MathPressoOperator operator = symbol.operator();
        int required = operator.operandCount();
        if (count != required) {
            throw reporter.refuse("Function '" + symbol.name() + "' requires " + required
                    + " argument(s) (" + count + " provided).");
        }
        MathPressoNode made;
        if (required == 1) {
            Unary unary = new Unary(operator);
            unary.setChildAt(0, arguments[0]);
            made = unary;
        } else {
            Binary binary = new Binary(operator);
            binary.setChildAt(1, arguments[1]);
            binary.setChildAt(0, arguments[0]);
            made = binary;
        }
        made.setPosition(position);
        return made;
    }
}
