Rebol []

box: context [a: 1]
evals-before: stats/evals
start: stats/timer
loop 1000000 [x: box/a]
print rejoin [
    "workload lookup evals " stats/evals - evals-before
    " ms " to integer! (stats/timer - start) / 0:00:00.001
]
