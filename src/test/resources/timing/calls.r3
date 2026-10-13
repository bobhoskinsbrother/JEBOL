Rebol []

one: func [x] [x]
evals-before: stats/evals
start: stats/timer
loop 1000000 [one 1]
print rejoin [
    "workload calls evals " stats/evals - evals-before
    " ms " to integer! (stats/timer - start) / 0:00:00.001
]
