package org.jebol.domain.eval;

import java.util.Comparator;
import java.util.List;

/**
 * Jingchao Chen's Adaptive Symmetry Partition Sort, ported index for index
 * from {@code f-adp-symmetry-psort.c} -- the {@code unstable_sort} behind
 * SORT/UNSTABLE. The exact permutation it leaves equal keys in is what
 * Rebol's own suite pins, so a faithful port is the only implementation
 * that answers it.
 */
final class SymmetryPartitionSort {

    private static final int P = 16;
    private static final int BETA1 = 256;
    private static final int BETA2 = 512;

    private SymmetryPartitionSort() {
    }

    static <T> void sort(List<T> items, Comparator<T> order) {
        int n = items.size();
        if (n < 2) {
            return;
        }
        int firstRunEnd = 1;
        int direction = 0;
        while (firstRunEnd < n) {
            int rc = order.compare(items.get(firstRunEnd - 1), items.get(firstRunEnd));
            if (rc != 0) {
                if (direction == 0) {
                    direction = rc < 0 ? 1 : -1;
                } else if (rc * direction > 0) {
                    break;
                }
            }
            firstRunEnd++;
        }
        int inversionBalance = direction * firstRunEnd;
        for (int at = firstRunEnd + 1; at < n; at += 97) {
            int rc = order.compare(items.get(at - 1), items.get(at));
            if (rc < 0) {
                inversionBalance++;
            }
            if (rc > 0) {
                inversionBalance--;
            }
        }
        int runEnd = firstRunEnd - 1;
        if (Math.abs(inversionBalance) > n / 512) {
            if (direction * inversionBalance < 0) {
                runEnd = 0;
                direction = -direction;
            }
            int walker = runEnd;
            while (true) {
                walker += 10;
                int backward = walker - 1;
                if (walker >= n) {
                    break;
                }
                while (walker < n && direction * order.compare(
                        items.get(walker - 1), items.get(walker)) <= 0) {
                    walker++;
                }
                while (backward > runEnd && direction * order.compare(
                        items.get(backward - 1), items.get(backward)) <= 0) {
                    backward--;
                }
                if (walker - backward < 4) {
                    continue;
                }
                if (runEnd != 0) {
                    int half = (walker - backward) / 2;
                    int quarter = runEnd / 4;
                    if (half > quarter) {
                        half = quarter;
                    }
                    int knot = 0;
                    while (knot < half && direction * order.compare(
                            items.get(runEnd - knot), items.get(backward + knot)) > 0) {
                        knot++;
                    }
                    if (knot >= half) {
                        continue;
                    }
                    runEnd = runEnd + 1 - knot;
                    backward = backward + knot;
                }
                if (backward != runEnd) {
                    while (backward < walker) {
                        swap(items, runEnd, backward);
                        runEnd++;
                        backward++;
                    }
                } else {
                    runEnd = walker;
                }
                runEnd--;
            }
        }
        int sortedPrefix = runEnd + 1;
        if (direction == -1) {
            int front = 0;
            while (front < runEnd) {
                swap(items, front, runEnd);
                front++;
                runEnd--;
            }
        }
        if (sortedPrefix < n) {
            partition(items, sortedPrefix, n, 0, order);
        }
    }

    private static <T> void partition(
            List<T> items, int sorted, int n, int base, Comparator<T> order) {
        int left = 0;
        int right = 0;
        while (true) {
            if (n < 8) {
                for (int step = 1; step < n; step++) {
                    int at = base + step;
                    while (order.compare(items.get(at - 1), items.get(at)) > 0) {
                        swap(items, at, at - 1);
                        at--;
                        if (at <= base) {
                            break;
                        }
                    }
                }
                return;
            }
            int m = Math.abs(sorted);
            int v;
            int pivot;
            int upper;
            if (m <= 2) {
                v = BETA2 > n ? n : 63;
                upper = base + v - 1;
                pivot = base + 1;
                swap(items, pivot, base + v / 2);
                if (order.compare(items.get(base), items.get(pivot)) > 0) {
                    swap(items, base, pivot);
                }
                if (order.compare(items.get(pivot), items.get(upper)) > 0) {
                    swap(items, pivot, upper);
                    if (order.compare(items.get(base), items.get(pivot)) > 0) {
                        swap(items, base, pivot);
                    }
                }
                left = 1;
                right = 1;
                upper--;
            } else {
                v = m > n / BETA1 ? n : P * m - 1;
                if (sorted < 0) {
                    if (v < n) {
                        left = m;
                        sorted = -sorted;
                    } else {
                        left = (m + 1) / 2;
                        right = m / 2;
                    }
                    for (int k = 0; k < left; k++) {
                        swap(items, base + k, base + (n - m) + k);
                    }
                    left--;
                }
                if (sorted > 0) {
                    int movingFrom = base + m;
                    int movingPast = base + v;
                    if (v < n) {
                        int stride = n / v;
                        int sampled = movingFrom;
                        for (int taking = movingFrom; taking < movingPast;
                                taking++, sampled += stride) {
                            swap(items, taking, sampled);
                        }
                    }
                    right = m / 2;
                    int moving = right;
                    do {
                        movingFrom--;
                        movingPast--;
                        swap(items, movingFrom, movingPast);
                        moving--;
                    } while (moving != 0);
                    left = (m - 1) / 2;
                }
                pivot = base + left;
                upper = pivot + (v - m);
            }
            int scanner = pivot + 1;
            int equals = pivot + 1;
            int rc = 1;
            do {
                while (scanner <= upper) {
                    rc = order.compare(items.get(scanner), items.get(pivot));
                    if (rc >= 0) {
                        break;
                    }
                    scanner++;
                }
                if (scanner >= upper) {
                    break;
                }
                if (rc == 0) {
                    if (equals != scanner) {
                        swap(items, scanner, equals);
                    }
                    equals++;
                    scanner++;
                    continue;
                }
                while (scanner <= upper) {
                    rc = order.compare(items.get(upper), items.get(pivot));
                    if (rc <= 0) {
                        break;
                    }
                    upper--;
                }
                if (scanner >= upper) {
                    break;
                }
                swap(items, scanner, upper);
                if (rc == 0) {
                    if (equals != scanner) {
                        swap(items, scanner, equals);
                    }
                    equals++;
                }
                scanner++;
                upper--;
            } while (scanner <= upper);
            int equalRun = equals - pivot;
            int unequalRun = scanner - equals;
            if (unequalRun < equalRun) {
                equals = pivot + unequalRun;
            }
            int mover = scanner;
            while (pivot < equals) {
                mover--;
                swap(items, mover, pivot);
                pivot++;
            }
            int belowPivot = scanner - base;
            if (right < v - belowPivot) {
                partition(items, -right, v - belowPivot, scanner, order);
            }
            belowPivot = belowPivot - equalRun;
            if (v < n) {
                if (left < belowPivot) {
                    partition(items, left, belowPivot, base, order);
                }
                sorted = v;
            } else {
                if (left >= belowPivot) {
                    return;
                }
                sorted = left;
                n = belowPivot;
            }
        }
    }

    private static <T> void swap(List<T> items, int here, int there) {
        T held = items.get(here);
        items.set(here, items.get(there));
        items.set(there, held);
    }
}
