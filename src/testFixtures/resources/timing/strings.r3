Rebol []

s: copy ""
evals-before: stats/evals
start: stats/timer
repeat i 1000000 [append s "x"]
print rejoin [
    "workload strings evals " stats/evals - evals-before
    " ms " to integer! (stats/timer - start) / 0:00:00.001
]
