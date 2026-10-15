Rebol []

evals-before: stats/evals
start: stats/timer
i: 0
loop 1000000 [i: i + 1]
print rejoin [
    "workload count-global evals " stats/evals - evals-before
    " ms " to integer! (stats/timer - start) / 0:00:00.001
]
