# The SQLite extension

`jebol-sqlite.jar` gives JEBOL the `sqlite` module and the `sqlite:` scheme of
[Rebol-SQLite 3.46.0.0](https://github.com/Siskin-framework/Rebol-SQLite), so a
script written for Rebol's SQLite runs on JEBOL unchanged. On the JVM the
database is [sqlite4j](https://github.com/roastedroot/sqlite4j), SQLite compiled
to WebAssembly and then to Java bytecode, so nothing native is unpacked. In the
browser it is sqlite.org's own `sqlite3.wasm`. Both are SQLite 3.54.1.

## Running with it

The jar and the libraries it names sit together in one folder. Put JEBOL's jar
and this one on the class path; `java -jar` would ignore it.

```sh
java -cp jebol-3.22.5.jar:jebol-sqlite/jebol-sqlite.jar org.jebol.adapter.cli.JebolLauncher script.r3
```

Without the jar, `import 'sqlite` fails as a module that cannot be found, as it
does on any Rebol that has not got it.

For a site, build it with the jar named and the browser gets the same module:

```sh
./gradlew assembleSite -PsiteDir=my-app -Pextensions=extensions/sqlite/build/jebol-extension/jebol-sqlite.jar
```

## The commands

The module exports nothing, so its `open` and `close` never hide the natives of
the same name. Keep the module in a word and reach the commands through it.

```rebol
sqlite: import 'sqlite
db: sqlite/open %shop.db
sqlite/exec db {
    CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT NOT NULL, price REAL);
    INSERT INTO products (name, price) VALUES ('kettle', 24.5), ('toaster', 31.0), ('mug', 6.25);
}
;== unset
```

`exec` runs any number of statements and prints any rows they answer, a
`name = value` line for each column and a blank line after each row. It answers
nothing; it is for setting things up and looking at them.

`eval` runs one statement and answers its rows laid out flat, one value for
each column of each row:

```rebol
sqlite/eval db {SELECT name, price FROM products ORDER BY price}
;== ["mug" 6.25 "kettle" 24.5 "toaster" 31.0]
```

A statement that answers no rows answers how many rows it changed:

```rebol
sqlite/eval db {UPDATE products SET price = price * 2 WHERE price < 10}
;== 1
```

### Values in, values out

Put the SQL in a block with `?` where each value goes. A value may be an
`integer!`, a `decimal!`, a `string!`, a `logic!` (stored as 1 or 0) or none
(stored as NULL). A word is looked up first.

```rebol
cheapest: 10
sqlite/eval db [{SELECT name FROM products WHERE price > ? ORDER BY name} cheapest]
;== ["kettle" "mug" "toaster"]
```

Values for several rows go one after another, or a block for each row. A row
that is short leaves the rest NULL:

```rebol
sqlite/eval db [
    {INSERT INTO products (name, price) VALUES (?, ?)}
    ["teapot" 18.0]
    ["spoon" 2.5]
    ["saucer"]
]
;== 3
sqlite/eval db {SELECT name FROM products WHERE price IS NULL}
;== ["saucer"]
```

What comes back: INTEGER is `integer!`, REAL is `decimal!`, TEXT is
`string!`, BLOB is `binary!` and NULL is none.

```rebol
sqlite/eval db {SELECT 42, 1.5, 'text', x'CAFE', NULL}
;== [42 1.5 "text" #{CAFE} _]
```

### Prepared statements

`prepare` compiles a statement once. `step` answers one row each time, then
none when there are no more, and `reset` starts it again. `columns` names what
it answers.

```rebol
by-price: sqlite/prepare db {SELECT name FROM products WHERE price < ? ORDER BY price}
sqlite/columns by-price
;== ["name"]
sqlite/step/with by-price [20]
;== ["spoon"]
sqlite/step by-price
;== ["mug"]
sqlite/step/rows by-price 0
;== ["teapot"]
```

`/rows` takes that many rows at once, and a count below one takes all that are
left. `step/with` binds without starting again, so reset first to run it over
with new values:

```rebol
sqlite/reset by-price
sqlite/step/with/rows by-price [30] 0
;== ["spoon" "mug" "teapot" "kettle"]
sqlite/finalize by-price
;== unset
```

A prepared statement also goes to `eval`, alone or with its values:

```rebol
named: sqlite/prepare db {SELECT price FROM products WHERE name = ?}
sqlite/eval db reduce [named "kettle"]
;== [24.5]
```

### Errors

A command that fails raises `command-fail`, carrying SQLite's own words:

```rebol
e: try [sqlite/exec db {INSERT INTO products (name) VALUES (NULL)}]
e/arg1
;== {[SQLITE] constraint failed NOT NULL constraint failed: products.name}
```

### The rest

`last-insert-id` answers the rowid of the last insert, `trace` prints what
SQLite runs (1 for each statement, 2 for its time, 4 for each row, 8 for the
close, added together), `info` describes the library or, with `/of`, a handle,
and `close` closes the database.

```rebol
sqlite/eval db [{INSERT INTO products (name, price) VALUES (?, ?)} "plate" 9.0]
sqlite/last-insert-id db
;== 7
sqlite/close db
;== unset
```

## The scheme

Importing the module also makes the `sqlite:` scheme. `open/new` makes the file
when it is not there, `write` runs statements as `exec` does, `insert` prepares
one, and `take`, `read` and `pick` answer its rows.

```rebol
shop: open sqlite:shop.db
insert shop {SELECT name FROM products ORDER BY name}
take shop
;== ["kettle"]
read/part shop 2
;== ["mug" "plate"]
pick shop {SELECT count(*) FROM products}
;== [7]
close shop
```

## Files

A database is read through the same port as `read` and written through the
same port as `write`, so `secure` and the host's grants apply to it as they do
to any file, and a relative path counts from `what-dir`. Each change is written
to the file as soon as no transaction is open, so a script that never closes its
database still leaves it on disk, and a second handle on the same file sees it.
`%:memory:` (written `to file! ":memory:"`, since `%:memory:` does not read) and
`%""` are databases that never touch a file.

## Where JEBOL differs

`docs/sqlite-port.md` lists every answer JEBOL gives that Rebol-SQLite's C does
not, with why. In short: JEBOL asks SECURE, where the C never does; a zero-length
blob is `#{}`, where the C crashes or repeats another column; `last-insert-id` on
a closed database is 0, where the C crashes; and `info` reports the SQLite the
engine runs, 3.54.1, while the module keeps its version, 3.46.0.0.

## Licences

Rebol-SQLite is MIT, and its scheme is carried here with two edits that
`docs/sqlite-port.md` lists. sqlite4j, Endive and ZeroFS are Apache-2.0.
SQLite is in the public domain.
