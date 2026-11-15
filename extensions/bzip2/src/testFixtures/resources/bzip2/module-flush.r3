Rebol []
b: import (to-rebol-file system/options/args/1)
e: b/make-encoder
print "about to flush"
probe b/write/flush :e "hello"
print "flush returned"
