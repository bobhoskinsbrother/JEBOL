Rebol []

fib: func [n] [either n < 2 [n] [(fib n - 1) + (fib n - 2)]]
evals-before: stats/evals
start: stats/timer
fib 24
print rejoin [
    "workload fib evals " stats/evals - evals-before
    " ms " to integer! (stats/timer - start) / 0:00:00.001
]
