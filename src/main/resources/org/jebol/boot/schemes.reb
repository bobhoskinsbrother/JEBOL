;; Rebol declares the schemes in sys-ports.reb's INIT-SCHEMES -- thirteen of
;; them, with their specs, their init functions and their awake handlers --
;; so that is what registers them. Five of those declarations used to be
;; written out again in boot files beside this one, which is how CALLBACK,
;; CLIPBOARD, SERIAL and UDP came to be declared in a file this build loads
;; and registered nowhere.
;;
;; Registering a name is not claiming a device. None of these declarations
;; carries an actor -- Rebol's actors are in the C and JEBOL's are in Java,
;; dispatched by scheme name either way -- so a name with nothing behind it
;; opens to a refusal that says which device is missing, which is what Rebol
;; does on a build with no clipboard compiled into it.
;;
;; INIT-SCHEMES also opens the standard ports on its last four lines, INPUT
;; and CALLBACK included; both were none here until it ran. It wires
;; DECODE-URL on its first line too, which is why that is not done here.
;;
;; This runs before the first borrowed file that registers a scheme of its
;; own. SYSTEM/SCHEMES is a block until INIT-SCHEMES turns it into an object,
;; and MAKE-SCHEME called against the block leaves an entry of a shape the
;; last line of INIT-SCHEMES cannot read back.
sys/init-schemes

;; BUNDLED is this build's own and Rebol has no equivalent: it fetches a
;; module over the wire where this one reads it out of itself.
sys/make-scheme [title: {Modules bundled with this build} name: 'bundled]
