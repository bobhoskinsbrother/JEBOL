#include "blend2d-rebol-extension.h"
#include <ctype.h>
#include <string.h>

extern RL_LIB *RL;
extern MyCommandPointer Command[];

typedef struct Cell { int type; RXIARG arg; } Cell;

static char *symbols[4096];
static int symbolCount = 1;

static u32 intern(const char *name) {
	char low[256]; int i = 0;
	for (; name[i] && i < 255; i++) low[i] = tolower((unsigned char)name[i]);
	low[i] = 0;
	for (int s = 1; s < symbolCount; s++) if (!strcmp(symbols[s], low)) return s;
	symbols[symbolCount] = strdup(low);
	return symbolCount++;
}

typedef struct Var { u32 sym; Cell cell; } Var;
static Var vars[512];
static int varCount = 0;

static void setVar(u32 sym, Cell cell) {
	for (int i = 0; i < varCount; i++) if (vars[i].sym == sym) { vars[i].cell = cell; return; }
	vars[varCount].sym = sym; vars[varCount].cell = cell; varCount++;
}

static Cell *getVar(u32 sym) {
	for (int i = 0; i < varCount; i++) if (vars[i].sym == sym) return &vars[i].cell;
	return NULL;
}

static REBSER *makeSeries(void *data, REBCNT tail, REBCNT rest, int wide) {
	REBSER *s = calloc(1, sizeof(REBSER));
	s->data = data; s->tail = tail; s->rest = rest; s->info = wide;
	return s;
}

static REBSER *makeImageSeries(int w, int h) {
	u32 *px = malloc(((size_t)w * h + 1) * 4);
	memset(px, 0xFF, (size_t)w * h * 4);
	REBSER *s = makeSeries(px, w * h, w * h + 1, 4);
	s->area.wide = w; s->area.high = h;
	return s;
}

static void rl_version(REBYTE v[]) { v[0] = 5; v[1] = 3; v[2] = 22; v[3] = 1; }

static void *rl_make_string(u32 size, int unicode) {
	int wide = unicode ? 2 : 1;
	return makeSeries(calloc(size + 1, wide), 0, size, wide);
}

static void *rl_make_image(u32 w, u32 h) {
	if (w > 0xFFFF || h > 0xFFFF) return 0;
	return makeImageSeries(w, h);
}

static u32 *rl_map_words(REBSER *series) {
	Cell *cells = (Cell *)series->data;
	u32 *words = malloc((series->tail + 2) * sizeof(u32));
	REBCNT i = 1;
	for (REBCNT n = 0; n < series->tail; n++) {
		int t = cells[n].type;
		if (t == RXT_WORD || t == RXT_SET_WORD || t == RXT_GET_WORD || t == RXT_LIT_WORD || t == RXT_REFINEMENT)
			words[i++] = cells[n].arg.int32a;
	}
	words[0] = i; words[i] = 0;
	return words;
}

static u32 rl_find_word(u32 *words, u32 word) {
	if (words == 0) return 0;
	for (REBCNT n = 1; n < words[0]; n++) if (words[n] == word) return n;
	return 0;
}

static int rl_get_value(REBSER *series, u32 index, RXIARG *result) {
	if (index >= series->tail) return 0;
	Cell *c = &((Cell *)series->data)[index];
	*result = c->arg;
	return c->type;
}

static int rl_get_value_resolved(REBSER *series, u32 index, RXIARG *result) {
	if (index >= series->tail) return 0;
	Cell *c = &((Cell *)series->data)[index];
	if (c->type == RXT_WORD || c->type == RXT_GET_WORD) {
		Cell *v = getVar(c->arg.int32a);
		if (!v) { result->int64 = 0; return RXT_UNSET; }
		*result = v->arg;
		return v->type;
	}
	*result = c->arg;
	return c->type;
}

static REBSER *rl_encode_utf8_string(void *src, REBCNT len, REBFLG uni, REBFLG opts) {
	unsigned char *out = malloc(len * 3 + 1); size_t o = 0;
	for (REBCNT i = 0; i < len; i++) {
		u32 c = uni ? ((u16 *)src)[i] : ((REBYTE *)src)[i];
		if (c < 0x80) out[o++] = c;
		else if (c < 0x800) { out[o++] = 0xC0 | (c >> 6); out[o++] = 0x80 | (c & 63); }
		else { out[o++] = 0xE0 | (c >> 12); out[o++] = 0x80 | ((c >> 6) & 63); out[o++] = 0x80 | (c & 63); }
	}
	out[o] = 0;
	return makeSeries(out, o, len * 3, 1);
}

static REBCNT handleSizes[16];
static int handleCount = 0;
static REBCNT handleBase = 1000;

static REBCNT rl_register_handle(REBYTE *name, REBCNT size, void *free_func) {
	handleSizes[handleCount] = size;
	return handleBase + handleCount++;
}

static REBHOB *rl_make_handle_context(REBCNT sym) {
	REBHOB *hob = calloc(1, sizeof(REBHOB));
	hob->data = calloc(1, handleSizes[sym - handleBase]);
	hob->sym = sym;
	return hob;
}

static const char *at;

static void skipSpace(void) {
	for (;;) {
		while (*at && isspace((unsigned char)*at)) at++;
		if (*at == ';') { while (*at && *at != '\n') at++; continue; }
		return;
	}
}

static int isDelimiter(char c) {
	return !c || isspace((unsigned char)c) || c == '[' || c == ']' || c == '(' || c == ')' || c == ';' || c == '"' || c == '{';
}

static REBSER *makeString(const unsigned char *utf8, size_t n) {
	u16 *wide = malloc((n + 1) * 2); size_t count = 0; int needsWide = 0;
	for (size_t i = 0; i < n;) {
		u32 c = utf8[i];
		if (c < 0x80) i += 1;
		else if ((c & 0xE0) == 0xC0) { c = ((c & 31) << 6) | (utf8[i + 1] & 63); i += 2; }
		else { c = ((c & 15) << 12) | ((utf8[i + 1] & 63) << 6) | (utf8[i + 2] & 63); i += 3; }
		if (c > 255) needsWide = 1;
		wide[count++] = c;
	}
	if (needsWide) {
		wide[count] = 0;
		return makeSeries(wide, count, count + 1, 2);
	}
	REBYTE *bytes = malloc(count + 1);
	for (size_t i = 0; i < count; i++) bytes[i] = wide[i];
	bytes[count] = 0;
	free(wide);
	return makeSeries(bytes, count, count + 1, 1);
}

static Cell readValue(void);

static REBSER *readBlockBody(char close) {
	Cell *cells = malloc(sizeof(Cell) * 4096); int n = 0;
	for (;;) {
		skipSpace();
		if (*at == close) { at++; break; }
		if (!*at) { fprintf(stderr, "unclosed block\n"); exit(2); }
		cells[n++] = readValue();
	}
	return makeSeries(cells, n, 4096, sizeof(Cell));
}

static void fillImage(REBSER *img, int r, int g, int b, int a) {
	u32 px = ((u32)a << 24) | ((u32)r << 16) | ((u32)g << 8) | (u32)b;
	u32 *p = (u32 *)img->data;
	for (REBCNT i = 0; i < img->tail; i++) p[i] = px;
}

static Cell readValue(void) {
	Cell c; memset(&c, 0, sizeof c);
	skipSpace();
	if (*at == '[') { at++; c.type = RXT_BLOCK; c.arg.series = readBlockBody(']'); c.arg.index = 0; return c; }
	if (*at == '"') {
		const char *s = ++at; while (*at != '"') at++;
		c.type = RXT_STRING; c.arg.series = makeString((const unsigned char *)s, at - s); c.arg.index = 0; at++;
		return c;
	}
	if (*at == '{') {
		const char *s = ++at; while (*at != '}') at++;
		c.type = RXT_STRING; c.arg.series = makeString((const unsigned char *)s, at - s); c.arg.index = 0; at++;
		return c;
	}
	if (*at == '%') {
		const char *s = ++at; while (!isDelimiter(*at)) at++;
		c.type = RXT_FILE; c.arg.series = makeString((const unsigned char *)s, at - s); c.arg.index = 0;
		return c;
	}
	if (!strncmp(at, "<noise ", 7)) {
		int w, h, seed, used = 0;
		sscanf(at, "<noise %dx%d %d>%n", &w, &h, &seed, &used);
		at += used;
		REBSER *img = makeImageSeries(w, h);
		u32 state = (u32)seed * 2654435761u + 1;
		u32 *p = (u32 *)img->data;
		for (REBCNT i = 0; i < img->tail; i++) { state = state * 1103515245u + 12345u; u32 x = state ^ (state >> 15); state = state * 1103515245u + 12345u; p[i] = x ^ (state << 7); }
		c.type = RXT_IMAGE; c.arg.image = img; c.arg.width = w; c.arg.height = h;
		return c;
	}
	if (*at == '<') {
		int w, h, r = 255, g = 255, b = 255, a = 255, used = 0;
		sscanf(at, "<image %dx%d %d.%d.%d.%d>%n", &w, &h, &r, &g, &b, &a, &used);
		if (!used) sscanf(at, "<image %dx%d>%n", &w, &h, &used);
		if (!used) { fprintf(stderr, "bad image literal\n"); exit(2); }
		at += used;
		REBSER *img = makeImageSeries(w, h);
		fillImage(img, r, g, b, a);
		c.type = RXT_IMAGE; c.arg.image = img; c.arg.width = w; c.arg.height = h;
		return c;
	}
	if (!strncmp(at, "#f64[", 5) || !strncmp(at, "#i32[", 5)) {
		int isDouble = at[1] == 'f'; at += 5;
		double *d = malloc(sizeof(double) * 4097); i32 *i = malloc(sizeof(i32) * 4097); int n = 0;
		for (;;) {
			skipSpace();
			if (*at == ']') { at++; break; }
			char *end; double v = strtod(at, &end); at = end;
			d[n] = v; i[n] = (i32)v; n++;
		}
		REBSER *s = makeSeries(isDouble ? (void *)d : (void *)i, n, n + 1, isDouble ? 8 : 4);
		s->size = isDouble ? VTSF64 : VTSI32;
		c.type = RXT_VECTOR; c.arg.series = s; c.arg.index = 0;
		return c;
	}
	const char *s = at;
	while (!isDelimiter(*at)) at++;
	char tok[256]; size_t n = at - s; memcpy(tok, s, n); tok[n] = 0;
	if (tok[0] == '\'') { c.type = RXT_LIT_WORD; c.arg.int32a = intern(tok + 1); return c; }
	if (n > 1 && tok[n - 1] == ':') { tok[n - 1] = 0; c.type = RXT_SET_WORD; c.arg.int32a = intern(tok); return c; }
	int dots = 0, hasX = 0, isNumber = (isdigit((unsigned char)tok[0]) || ((tok[0] == '-' || tok[0] == '+' || tok[0] == '.') && n > 1));
	for (size_t k = 0; k < n; k++) { if (tok[k] == '.') dots++; if (tok[k] == 'x' || tok[k] == 'X') hasX = 1; }
	if (isNumber && hasX) {
		char *x = strpbrk(tok, "xX"); *x = 0;
		c.type = RXT_PAIR; c.arg.pair.x = (float)strtod(tok, NULL); c.arg.pair.y = (float)strtod(x + 1, NULL);
		return c;
	}
	if (isNumber && dots >= 2) {
		c.type = RXT_TUPLE; int len = 0; char *p = tok;
		while (*p) { c.arg.tuple_bytes[len++] = (REBYTE)strtol(p, &p, 10); if (*p == '.') p++; }
		c.arg.tuple_len = len;
		return c;
	}
	if (isNumber && tok[n - 1] == '%') { tok[n - 1] = 0; c.type = RXT_PERCENT; c.arg.dec64 = strtod(tok, NULL) / 100.0; return c; }
	if (isNumber && (dots || strpbrk(tok, "eE"))) { c.type = RXT_DECIMAL; c.arg.dec64 = strtod(tok, NULL); return c; }
	if (isNumber) { c.type = RXT_INTEGER; c.arg.int64 = strtoll(tok, NULL, 10); return c; }
	c.type = RXT_WORD; c.arg.int32a = intern(tok);
	return c;
}

static void printPixels(REBSER *img, int w, int h) {
	u32 *p = (u32 *)img->data;
	printf("image %dx%d\n", w, h);
	for (int y = 0; y < h; y++) {
		for (int x = 0; x < w; x++) printf("%08x%c", p[y * w + x], x + 1 < w ? ' ' : '\n');
	}
}

static int callCommand(int which, RXIFRM *frm) {
	return Command[which](frm, NULL);
}

static void defineBuiltIns(void) {
	Cell t; memset(&t, 0, sizeof t);
	t.type = RXT_LOGIC; t.arg.int32a = 1; setVar(intern("true"), t); setVar(intern("on"), t); setVar(intern("yes"), t);
	t.arg.int32a = 0; setVar(intern("false"), t); setVar(intern("off"), t); setVar(intern("no"), t);
	memset(&t, 0, sizeof t); t.type = RXT_NONE; setVar(intern("none"), t);
}

static void initWords(void) {
	const char *code = B2D_EXT_INIT_CODE;
	const char *w = strstr(code, "init-words words: ");
	at = w + strlen("init-words words: ");
	Cell cmdWords = readValue();
	Cell argWords = readValue();
	RXIFRM frm; memset(&frm, 0, sizeof frm);
	frm.args[1] = cmdWords.arg; frm.args[2] = argWords.arg;
	callCommand(CMD_B2D_INIT_WORDS, &frm);
}

static int setArg(RXIFRM *frm, int n, Cell c) {
	frm->args[n] = c.arg;
	RXA_TYPE(frm, n) = c.type;
	return c.type;
}

static Cell resolve(Cell c) {
	if (c.type == RXT_WORD) { Cell *v = getVar(c.arg.int32a); if (v) return *v; }
	return c;
}

static Cell runOne(Cell head);

static Cell runCommand(int which, int argc) {
	RXIFRM frm; memset(&frm, 0, sizeof frm);
	RXA_COUNT(&frm) = argc;
	for (int n = 1; n <= argc; n++) setArg(&frm, n, resolve(readValue()));
	int r = callCommand(which, &frm);
	Cell out; memset(&out, 0, sizeof out);
	if (r == RXR_VALUE) { out.type = RXA_TYPE(&frm, 1); out.arg = frm.args[1]; }
	else if (r == RXR_ERROR) { printf("error %s\n", (char *)frm.args[1].series); out.type = RXT_NONE; }
	else if (r == RXR_NONE) { out.type = RXT_NONE; }
	else if (r == RXR_TRUE) { out.type = RXT_LOGIC; out.arg.int32a = 1; }
	else { out.type = RXT_UNSET; }
	return out;
}

static Cell runOne(Cell head) {
	u32 sym = head.arg.int32a;
	if (sym == intern("draw")) return runCommand(CMD_B2D_DRAW, 2);
	if (sym == intern("path")) return runCommand(CMD_B2D_PATH, 1);
	if (sym == intern("font")) return runCommand(CMD_B2D_FONT, 1);
	if (sym == intern("image")) return runCommand(CMD_B2D_IMAGE, 1);
	if (sym == intern("info")) return runCommand(CMD_B2D_INFO, 0);
	if (sym == intern("info/of")) {
		RXIFRM frm; memset(&frm, 0, sizeof frm);
		RXA_COUNT(&frm) = 2;
		Cell refinement; memset(&refinement, 0, sizeof refinement); refinement.type = RXT_LOGIC; refinement.arg.int32a = 1;
		setArg(&frm, 1, refinement);
		setArg(&frm, 2, resolve(readValue()));
		int r = callCommand(CMD_B2D_INFO, &frm);
		Cell out; memset(&out, 0, sizeof out);
		if (r == RXR_VALUE) { out.type = RXA_TYPE(&frm, 1); out.arg = frm.args[1]; } else out.type = RXT_NONE;
		return out;
	}
	if (sym == intern("print")) {
		Cell v = resolve(readValue());
		if (v.type == RXT_STRING) { REBSER *s = v.arg.series; printf("string %u\n%.*s\n", s->tail, (int)s->tail, (char *)s->data); }
		else if (v.type == RXT_IMAGE) printPixels(v.arg.image, v.arg.width, v.arg.height);
		else if (v.type == RXT_HANDLE) printf("handle %u\n", v.arg.handle.type);
		else printf("type %d\n", v.type);
		Cell none; memset(&none, 0, sizeof none); none.type = RXT_UNSET; return none;
	}
	fprintf(stderr, "unknown statement %s\n", symbols[sym]); exit(2);
}

int main(int argc, char **argv) {
	static RL_LIB lib;
	lib.version = rl_version;
	lib.make_string = rl_make_string;
	lib.make_image = rl_make_image;
	lib.map_words = rl_map_words;
	lib.find_word = rl_find_word;
	lib.get_value = rl_get_value;
	lib.get_value_resolved = rl_get_value_resolved;
	lib.encode_utf8_string = rl_encode_utf8_string;
	lib.register_handle = rl_register_handle;
	lib.make_handle_context = rl_make_handle_context;
	if (!RX_Init(0, &lib)) { fprintf(stderr, "RX_Init refused\n"); return 2; }
	initWords();
	defineBuiltIns();

	FILE *f = fopen(argv[1], "rb");
	fseek(f, 0, SEEK_END); long len = ftell(f); fseek(f, 0, SEEK_SET);
	char *text = malloc(len + 1); fread(text, 1, len, f); text[len] = 0; fclose(f);
	at = text;
	for (;;) {
		skipSpace();
		if (!*at) break;
		Cell head = readValue();
		if (head.type == RXT_SET_WORD) {
			skipSpace();
			const char *mark = at;
			Cell next = readValue();
			if (next.type == RXT_WORD && (next.arg.int32a == intern("draw") || next.arg.int32a == intern("path")
					|| next.arg.int32a == intern("font") || next.arg.int32a == intern("image")
					|| next.arg.int32a == intern("info") || next.arg.int32a == intern("info/of"))) {
				setVar(head.arg.int32a, runOne(next));
			} else {
				at = mark;
				setVar(head.arg.int32a, resolve(readValue()));
			}
			continue;
		}
		runOne(head);
	}
	return 0;
}
