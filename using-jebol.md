# **Using JEBOL**

This user guide explains how to execute REBOL scripts using JEBOL. For details on building and porting JEBOL itself, please consult docs/porting-guide.md and goals.md.

Sections 8 and 9 outline planned future documentation. Their scope and requirements are explicitly noted to clarify missing content.

---

## **1. What is JEBOL?**

JEBOL is a port of Rebol 3 (specifically Oldes/Rebol3 version 3.22.5) from C to the Java Virtual Machine (JVM). It allows host Java applications to instantiate an interpreter, execute REBOL scripts, and retrieve evaluation results.

JEBOL enforces two key security principles by default: strictly enforced execution time limits and complete isolation from external resources unless explicitly permitted by the host environment.

```java
Interpreter interpreter = Interpreter.create();  
ScriptOutcome outcome = interpreter.run("1 + 2");  
System.out.println(outcome.asHostValue());   // 3
```

Script execution errors, timeouts, or stack overflows do not throw Java exceptions. Instead, execution results are encapsulated in a ScriptOutcome object. If a Java throwable is caught, it indicates an internal error within JEBOL itself.

The JEBOL distribution artifact has no external third-party dependencies.

As a port of Oldes/Rebol3 ([https://github.com/Oldes/Rebol3](https://github.com/Oldes/Rebol3)), unmodified components of the original project are bundled inside the JAR archive. See the "Licence and Attribution" section for licensing details.

---

## **2. Installation and Execution**

**Prerequisite:** JEBOL requires Java 25 or higher. Running on earlier Java versions will result in unsupported class file version errors.

### **Running from the Distribution Package**

The binary release contains launch scripts for Unix and Windows, the compiled JAR file, documentation, and licensing files (LICENSE and NOTICE).

```commandline
unzip jebol-3.22.5.zip  
JAVA_HOME=/path/to/java25 jebol-3.22.5/bin/jebol
```
### **Building and Running from Source**

```
./gradlew installDist  
JAVA_HOME=$(ls -d ~/.gradle/jdks/*25*/*/Contents/Home | head -1)   
./build/install/jebol/bin/jebol
```
Launching the REBOL interactive console initializes start (similar to r3) and presents the startup banner. Enter REBOL expressions directly at the prompt:

```commandline
╔══════════════════════════════════════════════════════════════════════════╗  
║  REBOL/core 3.22.5 (JEBOL port)                                          ║  
...  
>> 2 + 3 * 4  
== 20  
>> name: "world"  append name "!"  
== "world!"  
>> divide 1 0  
** math error: division by zero
```
To evaluate a one-line expression and terminate, use the --do flag:

```
./build/install/jebol/bin/jebol --do "print 1 + 2"
```

Rebuild the application using `./gradlew installDist` following any modifications to Java source files.

You can pass a script file or an application directory path to the command line. When an application directory is specified, JEBOL executes `main.reb` using that directory as the working path for relative operations like `do %...` or `read %...`. 
Directories lacking a `main.reb` file will produce an error.

```
jebol my-script.reb  
jebol my-app/
```
Alternatively, pass script content via standard input:

```
cat my-script.reb | jebol
```
---

## **3. Embedding JEBOL in Java Applications**

Embedding JEBOL requires configuring bounds, defining optional extensions or variables, and executing the script:

```java
Extension shop = new Extension("shop", "1.0.0", List.of(new ExtensionCommand(  
"lookup-price", "{The price of one item.} item [string!]", call -> DecimalValue.of(priceOf(((StringValue) call.arguments().getFirst()).text())))));

Interpreter interpreter = Interpreter.withBounds(Bounds.standard().withWallClockLimit(Duration.ofMillis(200)).add(shop));
interpreter.define("vatRate", 0.2);
ScriptOutcome outcome = interpreter.run("multiply lookup-price {widget} add 1 vatRate");  
Object total = outcome.asHostValue();
```

`Bounds.standard()` sets default execution constraints: a 5-second wall-clock time limit, a maximum call stack depth of 10,000, and instruction checks every 1,000 steps. Host services and external capabilities are disabled by default.

Scripts interact with host Java logic exclusively through extensions declared in the Bounds configuration (detailed in Section 4).

REBOL string literals use curly braces `({...})`. This allows embedding strings inside Java string literals without quote escaping (e.g., "{widget}" instead of "widget").

An Interpreter instance is single-threaded and must be confined to one thread at a time. Concurrent execution requires multiple interpreter instances. The only thread-safe cross-thread method is cancel(), which halts execution from another thread.

Initial interpreter instantiation takes approximately 430 ms, settling to around 50 ms after JVM warmup. High-throughput applications creating per-request interpreters should account for initialization overhead (see goals.md and scripts/timing.py boot for benchmarks).

---

## **4. Capabilities and Security Model**

By default, scripts execute in a strict sandbox without access to file systems, network adapters, system clocks, consoles, or subprocesses.

Enabling external capabilities requires granting the corresponding service in Bounds and providing a port implementation to the interpreter:

| What the script must do                      | The service       | The port to supply                           |
|----------------------------------------------|-------------------|----------------------------------------------|
| Read, write, delete, rename, list files      | FILES             | useFileSystem(FileSystemPort.rootedAt(path)) |
| Know where a relative path starts            | WORKING_DIRECTORY | the same file port                           |
| Read the names and values of the environment | ENVIRONMENT       | useEnvironment(port)                         |
| Start another program and wait for it        | PROCESSES         | useProcesses(port)                           |
| Read a line from the operator                | CONSOLE           | useConsole(port)                             |
| Ask what the time is                         | CLOCK             | none                                         |
| Open a connection to another machine         | NETWORK           | useNetwork(port)                             |
| Fetch a url with HTTP or HTTPS               | WEB               | useHttp(port)                                |
| Trust a server's certificates over TLS       | CERTIFICATES      | useCertificateAuthority(authority)           |
| Ask the operator through a window            | WINDOWS           | useWindows(port) and useScreen(port)         |
| Read and write what the operator copied      | CLIPBOARD         | useClipboard(port)                           |
| List, hear and play MIDI instruments         | MIDI              | useMidiDevices(devices)                      |
```java
Interpreter interpreter = Interpreter.withBounds(  
Bounds.standard().grant(HostService.FILES));  
interpreter.useFileSystem(FileSystemPort.rootedAt(Path.of("/srv/scripts")).readOnly());
```

A host can explicitly mark capabilities as unavailable using lacking(...). Scripts requesting unavailable services receive a not_present error rather than not_granted.

useHttp(port) delegates HTTP/HTTPS requests to the host platform. If no HTTP port is supplied, REBOL's default scheme handler falls back to establishing direct TCP connections via the NETWORK service.

Each interpreter manages its working directory within the file port's rooted path. Because the underlying JVM process working directory remains fixed, directory state is isolated per interpreter instance.

Accessing an ungranted service raises a RAISED outcome error specifying one of three causes: not_granted (service exists but was not allowed), not_present (service unsupported by host), or never_portable (feature unsupported across hosts).

Explicit security errors prevent ambiguous failures, ensuring scripts can distinguish between missing permissions and empty or missing files.

### **Creating Custom Extensions**

For a complete guide and worked example, see `docs/writing-an-extension.md`. Below is a summary of the extension architecture.

Extensions allow the Java host to expose native commands to REBOL. An extension defines a name, version string, and a list of commands pairing REBOL specifications with Java handlers. Adding an extension to Bounds registers it across created interpreters.

```java
Extension camera = new Extension("camera", "1.0.0", List.of(  
new ExtensionCommand("snap", "{Takes a photo.} quality [integer!] /flash", call -> {
    boolean flash = call.refinements().contains("flash");  
    return call.handleOf("photo", takeAPhoto(call.arguments().getFirst(), flash));  
}),  
new ExtensionCommand("photo-size", "photo [handle!]", call -> IntegerValue.of(call.heldBy(call.arguments().getFirst(), Photo.class).bytes()))))  
                    .withHandleKind(new HandleKind("photo", Photo.class))  
                    .startingWith(postbox -> requireACamera());
Interpreter interpreter = Interpreter.withBounds(Bounds.standard().add(camera));
```
Extension behavior in REBOL:

* Command words are exported to lib as command! datatypes. Functions like spec-of and help inspect their signatures.
* Argument type validation occurs automatically before Java handlers are invoked. Passing invalid types raises an expect-arg error.
* Java handlers receive native REBOL value wrappers (e.g., FileValue) and return valid REBOL values.
* Custom errors can be thrown via call.failure(...). Uncaught Java exceptions inside extension commands translate to REBOL command-fail errors carrying the exception message.
* call.evaluated(block) evaluates block arguments within the active interpreter context.
* Extensions register under system/modules and can be imported using import '<extension-name>.

Extensions can embed initialization REBOL scripts executed after native command creation, facilitating custom schemes or codec registrations:
```java
.withScript("""  
sys/make-scheme [  
name: 'greet  
title: "Greetings"  
actor: [read: func [port] [greeting-for port/spec/host]]  
]""");

```

For example, read greet://world delegates execution to the registered scheme actor.

**Asynchronous Events and Callbacks:** Extensions receive a reference to the interpreter's Postbox. Calling postbox.post(port, "read") queues port events across threads, while postbox.callBack(...) schedules REBOL object method execution on the interpreter thread during wait cycles (e.g., wait [port 5]).

**Extension Refusal Reasons:** Extension installation is atomic; failure leaves no partial state. Call interpreter.extensions() to inspect status and refusal causes:

| Refusal        | Why                                                                                                                                                                      |
|----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| already-used   | A command is named like a word lib already holds: a native, an action, or one of Rebol's own functions such as secure. Between two extensions, the first installed wins. |
| bad-extension  | The name is blank, the version is not a tuple, a spec does not read, or two commands share a name.                                                                       |
| handle-exists  | A handle kind another extension declared holding a different class.                                                                                                      |
| extension-init | Your start step threw.                                                                                                                                                   |
```java
Extension clash = new Extension("clash", "1.0.0", List.of(  
new ExtensionCommand("add", "a b", call -> StringValue.of("mine"))));

Interpreter.withBounds(Bounds.standard().add(clash)).extensions();  
// [ExtensionReport[name=clash, refusal=Optional[already-used]]]

```

While lib prevents extensions from overriding existing command names, user scripts retain the standard REBOL ability to rebind words (e.g., lib/add: func [a b] [...]).

### **File Access Security Controls**

File system access undergoes a two-tier evaluation sequence:

1. **1. Host File System Root:** The file port restricts all file operations to a configured root directory. Path traversal attempts above this root are constrained to the root directory itself.
2. **2. REBOL Security Policies:** Fine-grained permission rules configured via SECURE. Policy rules can further restrict access but cannot override or expand the root boundary established by the host.

This ordering applies across all host capabilities: ungranted host capabilities cannot be re-enabled using secure allow within the script.

The following file structure illustrates traversal behavior. In error outputs below, directory paths are abbreviated for readability (/work/projects and /Users/ana), and standard ** Where: / ** Near: stack traces are omitted:
```
/work/projects/  
secret.txt  
shared/logo.txt  
app/  
main.r3  
notes.txt               "app notes"  
again.txt         ->    notes.txt                 (a link inside app)  
logo.txt          ->    ../shared/logo.txt        (a link out of app)  
assets/door.txt   ->    ../../secret.txt          (a link out of app)  
dangling.txt      ->    ../../outside-new.txt     (points at nothing yet)
```

### **Root Boundary Behavior**

Scripts observe the root folder as /. Path resolution attempting to climb above the root via .. terminates at /. Absolute paths map relative to the configured root rather than the host system root.

Symbolic links pointing within the root execute normally. Links pointing outside the root raise a security violation error displaying the path as requested, preventing host path disclosure. Symbolic link resolution supports up to 40 consecutive links before raising an error.

When using the CLI, --root /work/projects/app sets the root directory. Combined with -s (unrestricted security policy), file operations remain constrained by the root boundary:
```commandline
>> what-dir  
== %/  
>> read/string %again.txt  
== "app notes^/"  
>> read/string %../secret.txt          ; .. stops at the root: this is /secret.txt  
** Access error: cannot open: %../secret.txt reason: 3  
>> read/string %logo.txt               ; a link out of the root  
** Access error: security violation: %logo.txt  (refer to SECURE function)  
>> read/string %assets/door.txt        ; the same, one folder down  
** Access error: security violation: %assets/door.txt  (refer to SECURE function)  
>> write %dangling.txt "x"             ; writing through a link to nothing yet  
** Access error: security violation: %dangling.txt  (refer to SECURE function)  
>> exists? %logo.txt                   ; a link out looks like nothing there  
== _  
>> delete %logo.txt                    ; removes the link; shared/logo.txt stays
```

In Java, root restrictions are established during port initialization:
```java
Interpreter interpreter = Interpreter.withBounds(  
Bounds.standard().grant(HostService.FILES));  
interpreter.useFileSystem(FileSystemPort.rootedAt(Path.of("/work/projects/app")));

ScriptOutcome linked = interpreter.run("read %assets/door.txt");  
// RAISED: ** Access error: security violation: %assets/door.txt

```

Directory mutations (delete, rename, make directory) operate on the entry path itself rather than symlink targets. Ancestor path components must remain strictly within the root.

### **REBOL Policy Rules**

Embedded interpreters default to allowing operations unless restricted by SECURE or host bounds.

CLI execution triggers default initialization (matching r3), allowing reads and writes within the application and user settings directories while prompting or denying access elsewhere. Inspect active settings via secure query:
```rebol
[  
file ask  
%/work/projects/app/ allow  
%/Users/ana/.rebol/ allow  
%/Users/ana/ [allow read ask write allow execute]  
net allow  
secure allow  
protect allow  
debug allow  
envr allow  
call allow  
browse allow  
extension allow  
]
```

Executing jebol main.r3 within /work/projects/app demonstrates policy enforcement:
```
>> read/string %notes.txt  
== "app notes^/"  
>> read/string %again.txt             ; a link inside the folder is followed  
== "app notes^/"  
>> read/string %../secret.txt  
** Access error: security violation: %/work/projects/secret.txt  (refer to SECURE function)  
>> write %../text.txt "x"          ; refused, and nothing is text  
** Access error: security violation: %/work/projects/text.txt  (refer to SECURE function)  
>> read/string %logo.txt              ; judged by where the link leads  
** Access error: security violation: %/work/projects/shared/logo.txt  (refer to SECURE function)  
>> read/string %assets/door.txt  
** Access error: security violation: %/work/projects/secret.txt  (refer to SECURE function)
```

Interactive execution without a script context prompts for confirmation on ungranted file access. Non-interactive pipe executions deny access automatically. Use -s to bypass interactive prompts.

Uncaught policy violations terminate CLI execution with status code 1. Scripts can catch violations using standard error handlers (error category: access/security, argument: target file).

**Symlink Security Difference from R3:** Unlike original C-Rebol (which validates literal path strings), JEBOL evaluates resolved symlink target destinations. If a symlink points outside permitted boundaries, access is denied.

#### **CLI Security Flags**

| Command                      | What happens                                                                                                                                                         |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| jebol main.r3                | Rebol's default policy, as above.                                                                                                                                    |
| jebol -s main.r3             | Every policy allows. The script reads ../secret.txt.                                                                                                                 |
| jebol --secure allow main.r3 | The same as -s.                                                                                                                                                      |
| jebol +s main.r3             | Every policy asks, so start's own PROTECT is refused. The script never runs. The command line prints security violation: system/options/boot-level and exits with 1. |
| jebol --secure throw main.r3 | The same as +s.                                                                                                                                                      |
| jebol --secure quit main.r3  | Start's own PROTECT ends the process at once. Nothing is printed, and the exit status is 101.                                                                        |

Note: The behavior of +s matches Oldes/Rebol3 3.22.5 behavior, halting execution when system protection settings are violated.

#### **Programmatic Policy Restrictions**

Scripts can reduce their own permissions but cannot exceed host grants or root limits:
```
>> secure [file [allow read throw write]]  
>> read/string %notes.txt  
== "app notes^/"  
>> write %notes.txt "changed"  
** Access error: security violation: %/work/projects/app/notes.txt  (refer to SECURE function)

>> secure [envr throw call throw net throw debug throw]  
>> get-env "HOME"  
** Access error: security violation: "HOME"  (refer to SECURE function)  
>> call/wait "true"  
** Access error: security violation: "true"  (refer to SECURE function)  
>> open tcp://example.com:80  
** Access error: security violation: tcp://example.com:80  (refer to SECURE function)  
>> trace on                            ; no subject, so the class is named  
** Access error: security violation: debug  (refer to SECURE function)
>> secure [file 5]  
** access error: invalid file security policy: 5

**Evaluation Note:** SECURE receives unevaluated block arguments. Dynamic block composition requires parenthesized expressions: secure (compose [file throw (folder) allow]).

>> secure compose [file throw (folder) allow]  
** access error: invalid all security policy: compose
```

Security levels and actions:

| Level | What happens                                                                                            |
|-------|---------------------------------------------------------------------------------------------------------|
| allow | Permits the requested operation.                                                                        |
| ask   | Prompts interactively at the console. Without interactive console support, requests fail automatically. |
| throw | Raises a catchable REBOL security error identifying the target resource.                                |
| quit  | Terminates script execution immediately with exit status 101. Cannot be caught using try.               |

Java hosts can apply policies prior to running user code, bound by maximum host capability grants:
```java
interpreter.run("secure [file [allow read throw write]]");  
interpreter.run("write %notes.txt {changed}");  
// RAISED: ** Access error: security violation: %/notes.txt

interpreter.run("secure allow");  
interpreter.run("call/wait {true}");  
// RAISED: processes is not granted. SECURE cannot grant what the host held back.
```

#### **Checked Operations vs. Unchecked Operations**

JEBOL matches REBOL C security check placement precisely:

| Checked                                                                               | Against                         |
|---------------------------------------------------------------------------------------|---------------------------------|
| READ, LOAD and DO of a file. OPEN without /write, and OPEN/read.                      | file read                       |
| WRITE, WRITE/append, CREATE. OPEN without /read, OPEN/write and OPEN/new.             | file write                      |
| READ, OPEN, QUERY and EXISTS? of a folder                                             | file read                       |
| MAKE-DIR, DELETE and RENAME of a folder                                               | file write                      |
| OPEN of a tcp or udp port. HTTP and HTTPS are checked through the tcp port they open. | net execute, naming the address |
| CALL                                                                                  | call execute                    |
| BROWSE                                                                                | browse execute                  |
| GET-ENV / SET-ENV                                                                     | envr read / write               |
| PROTECT / UNPROTECT                                                                   | protect write / read            |
| EVOKE, STACK, TRACE                                                                   | debug read                      |
| LOAD-EXTENSION of a file                                                              | extension execute               |

The following operations do not perform policy checks (matching C behavior), though they remain strictly constrained by the host file system root:
```
>> size? %../secret.txt  
== 7  
>> rename %../secret.txt %../renamed.txt    ; goes ahead  
>> delete %../renamed.txt                   ; goes ahead  
>> exists? %../shared/                      ; a folder: queried, refused, so none  
== _
```
* DELETE, RENAME, QUERY and MODIFY of a file. This includes SIZE?, MODIFIED? and EXISTS? of a file.
* ECHO and IMAGE/SAVE, which write without the file actor.
* A port that is already open. OPEN was checked, and reads and writes after it are not.
* LIST-ENV, PROTECTED? and reading a dns:// url.
* The eval and memory limits. LIMIT-USAGE is gone before any script runs, in R3 as here, so secure [eval 1000] fails with limit-usage has no value.

File root constraints remain active across all unchecked operations.

---

## **5. Execution Outcomes and Termination**

Every script execution returns a ScriptOutcome representing one of six outcome conclusions. Inspect status using outcome.conclusion() or outcome.succeeded():

| Conclusion       | What it means                                                 | Where the value is      |
|------------------|---------------------------------------------------------------|-------------------------|
| PRODUCED_A_VALUE | The script finished.                                          | asHostValue()           |
| RAISED           | The script failed, and it could have caught the error itself. | errorId() and display() |
| TIMED_OUT        | The time limit passed first.                                  | none                    |
| CANCELLED        | The host called cancel().                                     | none                    |
| QUIT_EARLY       | The script called QUIT.                                       | asHostValue()           |
| HALTED           | The script called HALT and gave control back.                 | none                    |

**Note:** Distinguish between CANCELLED (host initiated termination) and QUIT_EARLY (script self-termination via QUIT).

Always verify outcome.succeeded() before calling asHostValue(), as failed executions return the error representation when coerced directly:
```java
ScriptOutcome outcome = interpreter.run(source);  
if (!outcome.succeeded()) {  
    log.warn("script stopped: {} {}", outcome.conclusion(), outcome.display());  
    return fallbackValue;  
}  
return outcome.asHostValue();
```
Interpreter instances remain valid and reusable after script termination, timeouts, or errors. Execution state transitions leave data structures in a consistent state.

---

## **6. Data Type Conversion between Java and REBOL**

Data values map naturally between Java and REBOL types. Unmapped types are preserved as host object wrappers to avoid lossy conversions.

**Java to REBOL Type Mapping (via** define**):**

| Java                       | REBOL                                                                   |
|----------------------------|-------------------------------------------------------------------------|
| Boolean                    | logic!                                                                  |
| Long, Integer, Short, Byte | integer!                                                                |
| Double, Float              | decimal!                                                                |
| BigDecimal                 | money!                                                                  |
| String, Character          | string!                                                                 |
| List<?>                    | block!                                                                  |
| a REBOL value              | itself                                                                  |
| null                       | Wrapped as java-object! containing Java null (distinct from REBOL none) |
| anything else              | java-object!, held and not converted                                    |

**REBOL to Java Type Mapping (via** asHostValue()**):**

| REBOL                                                     | Java                         |
|-----------------------------------------------------------|------------------------------|
| integer!                                                  | long                         |
| decimal!, percent!                                        | double                       |
| money!                                                    | BigDecimal                   |
| logic!                                                    | boolean                      |
| string!, file!, url!, tag!, email! and every other string | String                       |
| block!, paren!, path! and every other block               | List\<Object>                |
| java-object!                                              | the object it holds          |
| none!, unset!                                             | null                         |
| anything else                                             | the molded text, as a String |

Java null and REBOL none are kept distinct during conversion. Use asOptionalHostValue() to map both to an empty Java Optional.

---

## **7. Known Limitations and Status**

Review this section prior to integration. It details feature coverage, pending work, and intentional deviations from C-Rebol.

**Test Suite Parity:** All assertions in REBOL's test suite either pass in JEBOL or represent known failures present in standard Oldes/Rebol3 3.22.5 (documented in fails-on-rebol-too.txt). Intentional architectural differences are tracked in goals.md.

**Datatypes and System Features:** All standard R3 datatypes are implemented. task! execution requires host thread support enabled via useTasks. OS user context fields in access-os (uid, euid, gid, egid) return not-here due to JVM platform abstractions. Scheme names match R3 registrations, failing at open time if unhandled by the host.

**TLS Transport Security:** Unlike stock R3 3.22.5, JEBOL enforces strict TLS certificate validation. HTTPS connections verify server certificates using host trust stores configured via CERTIFICATES grants.

**Memory Management:** Memory exhaustion raises REBOL no-memory errors which scripts can catch. Process heap allocation should be restricted at the JVM level when executing untrusted scripts.

**Graphics and Rendering:** DRAW supports all dialect commands except effect. VID is not included. Rendering parity between Java2D and browser targets is verified via automated end-to-end tests (./gradlew endToEndTests).

For current test statistics and benchmark numbers, run ./gradlew allTests.

---

## **8. Worked Examples (Pending Documentation)**

**Upcoming content:** This section will include six fully executable reference programs with accompanying automated tests, covering the following integration scenarios:

* a script under a time limit
* a call from the script back into Java
* a read of a file through a granted directory
* a script that fails, and a host that handles the failure
* a small dialect

These samples will be bundled directly within the distribution package.

Automated test execution ensures code examples remain accurate across releases.

---

## **9. Word Reference (Pending Documentation)**

**Upcoming content:** This section will provide an automatically generated reference listing all functions, signatures, and arguments exported by the interpreter engine.

Reference generation utilizes API parity reports generated by scripts/c-surface.py and scripts/c-parity.py.

---

## **Further Reading**

Additional specifications available in the source repository:

| The question                                        | The file            |
|-----------------------------------------------------|---------------------|
| What a host can decide, and what a script can reach | spec/embed.allium   |
| What PARSE supports, keyword by keyword             | spec/parse.allium   |
| How a dialect is read by DELECT                     | spec/dialect.allium |
| What a REBOL value is                               | spec/values.allium  |
| What each function does                             | spec/natives.allium |
| How source text becomes values                      | spec/load.allium    |
| How a block is evaluated                            | spec/eval.allium    |

Specification files reflect target language behaviors verified against C-Rebol. Known implementation differences in JEBOL are detailed in Section 7.

---

## **Licence and Attribution**

JEBOL is Copyright © 2026 Ben Hoskins, licensed under the Apache License, Version 2.0. The full license text is available in the LICENSE file included with the distribution.

**Oldes/Rebol3 Port Attribution:** JEBOL is a port of Oldes/Rebol3 ([https://github.com/Oldes/Rebol3](https://github.com/Oldes/Rebol3)), which derives from REBOL (Copyright © 2012 REBOL Technologies, Copyright © 2012–2026 Rebol Open Source Contributors) under the Apache License 2.0.

**Bundled REBOL Mezzanine Libraries:** Files located under org/jebol/mezz/ inside the JAR are upstream REBOL mezzanine scripts, modified only in prot-tls.reb (adding certificate checks) and mezz-banner.reb (updating the startup banner). Each file preserves its respective copyright header.

See the NOTICE file for complete attribution listings, which must be retained when redistributing JEBOL.

REBOL is a registered trademark of REBOL Technologies. JEBOL is an independent project and is not affiliated with or endorsed by REBOL Technologies or the maintainers of Oldes/Rebol3.

