Rebol []

count-up: func [times /local i] [
    i: 0
    loop times [i: i + 1]
    i
]
evals-before: stats/evals
start: stats/timer
count-up 1000000
print rejoin [
    "workload count-local evals " stats/evals - evals-before
    " ms " to integer! (stats/timer - start) / 0:00:00.001
]
