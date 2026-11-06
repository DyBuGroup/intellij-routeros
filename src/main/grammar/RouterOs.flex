package dev.dybug.routeros.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;

import static dev.dybug.routeros.psi.RosTypes.*;

%%

%{
  private int[] stack = new int[16];
  private int[] hashes = new int[16];
  private char[] openers = new char[16];
  // open brackets of each kind since the innermost string boundary, and their values before each push
  private int parens = 0, squares = 0, braces = 0;
  private long[] savedCounts = new long[16];
  private int depth = 0;
  private int hash = 0;
  private int stringFrames = 0;

  public _RouterOsLexer() {
    this((java.io.Reader) null);
  }

  /** Clears the bracket/string state stack; called whenever lexing (re)starts. */
  public void resetStack() {
    depth = 0;
    hash = 0;
    stringFrames = 0;
    parens = 0;
    squares = 0;
    braces = 0;
  }

  public boolean isStackEmpty() {
    return depth == 0;
  }

  /** A fingerprint of the state stack, maintained incrementally so it is O(1) per token. */
  public int stackHash() {
    return hash;
  }

  private void push(int returnState, char opener) {
    if (depth == stack.length) {
      stack = java.util.Arrays.copyOf(stack, depth * 2);
      hashes = java.util.Arrays.copyOf(hashes, depth * 2);
      openers = java.util.Arrays.copyOf(openers, depth * 2);
      savedCounts = java.util.Arrays.copyOf(savedCounts, depth * 2);
    }
    savedCounts[depth] = ((long) parens << 42) | ((long) squares << 21) | braces;
    switch (opener) {
      case '(': parens++; break;
      case '[': squares++; break;
      case '{': braces++; break;
      case '"': parens = 0; squares = 0; braces = 0; break;
      default: break;
    }
    hashes[depth] = hash;
    openers[depth] = opener;
    stack[depth++] = returnState;
    hash = (hash * 31 + returnState) * 131 + opener + 1;
    if (returnState == STRING) stringFrames++;
  }

  private void pop() {
    if (depth == 0) {
      yybegin(YYINITIAL);
      return;
    }
    depth--;
    hash = hashes[depth];
    long saved = savedCounts[depth];
    parens = (int) (saved >>> 42);
    squares = (int) ((saved >>> 21) & 0x1FFFFF);
    braces = (int) (saved & 0x1FFFFF);
    if (stack[depth] == STRING) stringFrames--;
    yybegin(stack[depth]);
  }

  private void enter(int returnState, int newState) {
    char opener = yylength() > 0 ? yycharat(yylength() - 1) : ' ';
    push(returnState, opener);
    yybegin(newState);
  }

  /**
   * Closes the innermost frame opened by the bracket matching [closer]. Frames opened by other
   * brackets in between are unwound (the input is broken there anyway), so that a missing ']' does
   * not leave the lexer inside an expression for the rest of the file. A closer without any
   * matching opener (stopping at string boundaries) leaves the state unchanged.
   */
  private void closeBracket(char closer) {
    char opener = closer == ')' ? '(' : closer == ']' ? '[' : '{';
    int open = closer == ')' ? parens : closer == ']' ? squares : braces;
    if (open == 0) return; // O(1) for unmatched closers; a successful scan pops what it walks over
    for (int i = depth - 1; i >= 0; i--) {
      if (openers[i] == opener) {
        while (depth > i + 1) pop();
        pop();
        return;
      }
      if (openers[i] == '"') return;
    }
  }

  /** True when the lexer is inside $(...) or $[...] interpolation of a string literal. */
  private boolean inInterpolation() {
    return stringFrames > 0;
  }

  /** Do not swallow the '-' of a following "->" into a word. */
  private void unglueArrow() {
    if (yylength() > 1 && yycharat(yylength() - 1) == '-'
        && zzMarkedPos < zzEndRead && zzBuffer.charAt(zzMarkedPos) == '>') {
      yypushback(1);
    }
  }

  private IElementType scriptCommand() {
    String name = yytext().subSequence(1, yylength()).toString();
    yybegin(SARGS);
    switch (name) {
      case "local": return CMD_LOCAL;
      case "global": return CMD_GLOBAL;
      case "set": return CMD_SET;
      default: return SCRIPT_CMD;
    }
  }

  private static final java.util.Set<String> CONTROL_ARGS = java.util.Set.of("do", "else", "on-error");
  private static final java.util.Set<String> SCRIPT_CONTROL_ARGS = java.util.Set.of(
      "do", "else", "on-error", "from", "to", "step", "in", "while", "command", "delay", "max",
      "script", "file", "as-string");

  private IElementType argName(boolean scriptCommand) {
    String name = yytext().toString();
    return (scriptCommand ? SCRIPT_CONTROL_ARGS : CONTROL_ARGS).contains(name) ? CONTROL_ARG : ARG_NAME;
  }
%}

%public
%class _RouterOsLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL = \r\n | \r | \n
WS = [ \t\f]+
CONT = \\ [ \t\f]* {EOL}
COMMENT = "#" [^\r\n]*

VAR_NAME = [A-Za-z0-9_]+
CMD_NAME = [A-Za-z] [A-Za-z0-9\-]*

PATH_WORD = [^ \t\f\r\n;=\[\](){}\"#$\\/<>]+
NUMERIC_WORD = [0-9]+ ([.:,\-/] [^ \t\f\r\n;=\[\](){}\"#$\\<>]*)?
ITEM_ID = \*[0-9A-Fa-f]+

ARG_WORD = [^ \t\f\r\n;=\[\](){}\"#$\\<>] [^ \t\f\r\n;=\[\](){}\"\\<>]*
VALUE_WORD = [^ \t\f\r\n;\[\](){}\"#$\\<>] [^ \t\f\r\n;\[\](){}\"\\<>]*
WHERE_WORD = [^ \t\f\r\n;=!<>~&|()\[\]{}\"#$\\,]+

DEC = [0-9]+
HEX = 0x [0-9A-Fa-f]+
IPV4 = [0-9]{1,3} (\. [0-9]{1,3}){3} (\/ [0-9]{1,2})?
COLON_LITERAL = [0-9A-Fa-f]* ":" [0-9A-Fa-f:]* (\. [0-9]+)* (\/ [0-9]+)?
TIME = ([0-9]+ ("w" | "d" | "h" | "m" | "s" | "ms" | "us" | "ns"))+
EXPR_WORD = [A-Za-z_] ([A-Za-z0-9_] | "-" [A-Za-z0-9_])*

%state PATH, ARGS, VALUE, SARGS, SVALUE, WHERE, EXPR, STRING, ESC_STRING, INTERP_OPEN

%%

// ---------------------------------------------------------------- shared rules for code states
<YYINITIAL, PATH, ARGS, VALUE, SARGS, SVALUE, WHERE, EXPR> {
  {CONT}                 { return TokenType.WHITE_SPACE; }
  \\ \"                  { if (inInterpolation()) { enter(yystate(), ESC_STRING); return STRING_START; }
                           return TokenType.BAD_CHARACTER; }
  \"                     { enter(yystate() == YYINITIAL || yystate() == PATH ? ARGS : yystate(), STRING); return STRING_START; }
  "$" {VAR_NAME}         { if (yystate() == YYINITIAL) yybegin(SARGS); else if (yystate() == PATH) yybegin(ARGS);
                           return DOLLAR_NAME; }
  "$" / \"               { if (yystate() == YYINITIAL) yybegin(SARGS); else if (yystate() == PATH) yybegin(ARGS);
                           return DOLLAR; }
  // $\"quoted name\" inside $( … ) / $[ … ] of a string literal
  "$" / \\ \"            { if (!inInterpolation()) return TokenType.BAD_CHARACTER;
                           if (yystate() == YYINITIAL) yybegin(SARGS); else if (yystate() == PATH) yybegin(ARGS);
                           return DOLLAR; }
  "]" | "}" | ")"        { char c = yycharat(0);
                           closeBracket(c);
                           return c == ']' ? RBRACKET : c == '}' ? RBRACE : RPAREN; }
}

<YYINITIAL, PATH, ARGS, VALUE, SARGS, SVALUE, WHERE> {
  {WS}                   { if (yystate() == VALUE) yybegin(ARGS); else if (yystate() == SVALUE) yybegin(SARGS);
                           return TokenType.WHITE_SPACE; }
  {EOL}                  { yybegin(YYINITIAL); return NEWLINE; }
  ";"                    { yybegin(YYINITIAL); return SEMICOLON; }
  {COMMENT}              { return COMMENT; }
}

// ---------------------------------------------------------------- statement start
<YYINITIAL> {
  ":" {CMD_NAME}         { return scriptCommand(); }
  "/"                    { yybegin(PATH); return SLASH; }
  "{"                    { enter(YYINITIAL, YYINITIAL); return LBRACE; }
  "["                    { enter(ARGS, YYINITIAL); return LBRACKET; }
  "("                    { enter(ARGS, EXPR); return LPAREN; }
  [^]                    { yypushback(1); yybegin(PATH); }
}

// ---------------------------------------------------------------- menu path and command word(s)
<PATH> {
  "/"                    { return SLASH; }
  "where" / [ \t\f\r\n]  { yybegin(WHERE); return WHERE_KW; }
  {ARG_WORD} / "="       { unglueArrow(); yybegin(ARGS); return argName(false); }
  // `name =value` is an error on the router, but lexing it as an argument gives a precise inspection
  {ARG_WORD} / [ \t]+ "=" { unglueArrow(); yybegin(ARGS); return argName(false); }
  {NUMERIC_WORD}         { yybegin(ARGS); return WORD; }
  {ITEM_ID}              { yybegin(ARGS); return WORD; }
  "!" {ARG_WORD}         { yybegin(ARGS); return WORD; }
  {PATH_WORD}            { return PATH_WORD; }
  "{"                    { enter(ARGS, YYINITIAL); return LBRACE; }
  "["                    { enter(ARGS, YYINITIAL); return LBRACKET; }
  "("                    { enter(ARGS, EXPR); return LPAREN; }
  "="                    { yybegin(VALUE); return EQ; }
  "->"                   { yybegin(ARGS); return ARROW; }
  "<"                    { yybegin(ARGS); return LT; }
  ">"                    { yybegin(ARGS); return GT; }
}

// ---------------------------------------------------------------- arguments of menu commands and script commands
<ARGS> {
  "where" / [ \t\f\r\n]  { yybegin(WHERE); return WHERE_KW; }
  {ARG_WORD} / "="       { unglueArrow(); return argName(false); }
  {ARG_WORD} / [ \t]+ "=" { unglueArrow(); return argName(false); }
  "="                    { yybegin(VALUE); return EQ; }
}

<SARGS> {
  {ARG_WORD} / "="       { unglueArrow(); return argName(true); }
  {ARG_WORD} / [ \t]+ "=" { unglueArrow(); return argName(true); }
  "="                    { yybegin(SVALUE); return EQ; }
}

<ARGS, SARGS> {
  "->"                   { return ARROW; }
  "{"                    { enter(yystate(), YYINITIAL); return LBRACE; }
  "["                    { enter(yystate(), YYINITIAL); return LBRACKET; }
  "("                    { enter(yystate(), EXPR); return LPAREN; }
  "<"                    { return LT; }
  ">"                    { return GT; }
  {ARG_WORD}             { unglueArrow(); return WORD; }
}

<VALUE, SVALUE> {
  "->"                   { return ARROW; }
  "{"                    { enter(yystate(), YYINITIAL); return LBRACE; }
  "["                    { enter(yystate(), YYINITIAL); return LBRACKET; }
  "("                    { enter(yystate(), EXPR); return LPAREN; }
  "<"                    { return LT; }
  ">"                    { return GT; }
  {VALUE_WORD}           { unglueArrow(); return WORD; }
}

// ---------------------------------------------------------------- where clause (print where ..., find where ...)
<WHERE> {
  "&&"                   { return ANDAND; }
  "||"                   { return OROR; }
  "!="                   { return NEQ; }
  "<="                   { return LE; }
  ">="                   { return GE; }
  "="                    { return EQ; }
  "<"                    { return LT; }
  ">"                    { return GT; }
  "!"                    { return NOT; }
  "~"                    { return TILDE; }
  ","                    { return COMMA; }
  "&"                    { return BITAND; }
  "|"                    { return BITOR; }
  "->"                   { return ARROW; }
  "and"                  { return AND_KW; }
  "or"                   { return OR_KW; }
  "in"                   { return IN_KW; }
  "not"                  { return NOT_KW; }
  "{"                    { enter(WHERE, YYINITIAL); return LBRACE; }
  "["                    { enter(WHERE, YYINITIAL); return LBRACKET; }
  "("                    { enter(WHERE, EXPR); return LPAREN; }
  {WHERE_WORD}           { unglueArrow(); return WORD; }
}

// ---------------------------------------------------------------- expressions inside ( ... )
<EXPR> {
  ({WS} | {EOL})+        { return TokenType.WHITE_SPACE; }
  {COMMENT}              { return COMMENT; }
  "("                    { enter(EXPR, EXPR); return LPAREN; }
  "["                    { enter(EXPR, YYINITIAL); return LBRACKET; }
  "{"                    { enter(EXPR, YYINITIAL); return LBRACE; }
  ";"                    { return SEMICOLON; }

  {HEX}                  { return NUMBER; }
  {DEC}                  { return NUMBER; }
  {IPV4}                 { return LITERAL; }
  {COLON_LITERAL}        { return LITERAL; }
  {TIME}                 { return LITERAL; }
  "true" | "false" | "yes" | "no" { return BOOLEAN; }
  "and"                  { return AND_KW; }
  "or"                   { return OR_KW; }
  "in"                   { return IN_KW; }
  "not"                  { return NOT_KW; }
  {EXPR_WORD}            { return WORD; }

  "->"                   { return ARROW; }
  "<%%"                  { return APPLY; }
  "&&"                   { return ANDAND; }
  "||"                   { return OROR; }
  "!="                   { return NEQ; }
  "<="                   { return LE; }
  ">="                   { return GE; }
  "<<"                   { return SHL; }
  ">>"                   { return SHR; }
  "="                    { return EQ; }
  "<"                    { return LT; }
  ">"                    { return GT; }
  "!"                    { return NOT; }
  "+"                    { return PLUS; }
  "-"                    { return MINUS; }
  "*"                    { return MUL; }
  "/"                    { return DIV; }
  "%"                    { return MOD; }
  "&"                    { return BITAND; }
  "|"                    { return BITOR; }
  "^"                    { return XOR; }
  "~"                    { return TILDE; }
  "."                    { return DOT; }
  ","                    { return COMMA; }
}

// ---------------------------------------------------------------- string literals with interpolation
<STRING> {
  \"                     { pop(); return STRING_END; }
  \\ {EOL}               { return STRING_ESCAPE; }
  \\ [\"\\nrt$_abfv]     { return STRING_ESCAPE; }
  \\ [0-9A-F][0-9A-F]    { return STRING_ESCAPE; }
  \\ [^]                 { return STRING_INVALID_ESCAPE; }
  \\                     { return STRING_INVALID_ESCAPE; }
  "$" {VAR_NAME}         { return DOLLAR_NAME; }
  "$" / [\(\[]           { yybegin(INTERP_OPEN); return DOLLAR; }
  "$"                    { return STRING_TEXT; }
  [^\"\\$]+              { return STRING_TEXT; }
}

<INTERP_OPEN> {
  "("                    { enter(STRING, EXPR); return LPAREN; }
  "["                    { enter(STRING, YYINITIAL); return LBRACKET; }
  [^]                    { yypushback(1); yybegin(STRING); }
}

// \"...\" nested inside $( ... ) / $[ ... ] interpolation
<ESC_STRING> {
  \\ \"                  { pop(); return STRING_END; }
  \\ [^\"]               { return STRING_ESCAPE; }
  \\                     { return STRING_INVALID_ESCAPE; }
  [^\\]+                 { return STRING_TEXT; }
}

[^]                      { return TokenType.BAD_CHARACTER; }
