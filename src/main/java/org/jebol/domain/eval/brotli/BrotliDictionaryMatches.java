package org.jebol.domain.eval.brotli;

import java.util.Arrays;

/** The shape below is the C's, converted mechanically. Do not tidy it. */
final class BrotliDictionaryMatches {

    private BrotliDictionaryMatches() {
    }

    static final int LONGEST_MATCH = 37;

    static final int NOTHING_FOUND_WHICH_IS_SEVEN_FS_NOT_EIGHT = 0xFFFFFFF;

    private static final long CUTTING_TRANSFORM_FOR_EACH_AMOUNT = 0x071B520ADA2D3200L;
    private static final int UPPERCASE_FIRST = 10;
    private static final int MIXING_MULTIPLIER = 0x1E35A7BD;

    static int[] room() {
        int[] matches = new int[LONGEST_MATCH + 1];
        Arrays.fill(matches, NOTHING_FOUND_WHICH_IS_SEVEN_FS_NOT_EIGHT);
        return matches;
    }

    private static int hash15(byte[] data, int at) {
        int fourBytes = (data[at] & 0xFF)
                | ((data[at + 1] & 0xFF) << 8)
                | ((data[at + 2] & 0xFF) << 16)
                | ((data[at + 3] & 0xFF) << 24);
        return (fourBytes * MIXING_MULTIPLIER) >>> (32 - 15);
    }

    private static void addMatch(int[] matches, int distance, int length,
            int lengthCode) {

        int packed = (distance << 5) + lengthCode;
        if (Integer.compareUnsigned(packed, matches[length]) < 0) {
            matches[length] = packed;
        }
    }

    private static int howMuchOfTheWordMatches(byte[] data, int at, int id,
            int length, int mostThatCouldMatch) {

        byte[] words = BrotliDictionary.words();
        int wordAt = BrotliDictionary.offsetFor(length) + length * id;
        int limit = Math.min(length, mostThatCouldMatch);
        int agreeing = 0;
        while (agreeing < limit && words[wordAt + agreeing] == data[at + agreeing]) {
            agreeing++;
        }
        return agreeing;
    }

    private static boolean wordMatches(byte[] data, int at, int wordLength,
            int wordTransform, int wordIndex, int mostThatCouldMatch) {

        if (wordLength > mostThatCouldMatch) {
            return false;
        }
        byte[] words = BrotliDictionary.words();
        int wordAt = BrotliDictionary.offsetFor(wordLength)
                + wordLength * wordIndex;
        if (wordTransform == 0) {
            for (int each = 0; each < wordLength; each++) {
                if (words[wordAt + each] != data[at + each]) {
                    return false;
                }
            }
            return true;
        }
        if (wordTransform == UPPERCASE_FIRST) {
            int first = words[wordAt] & 0xFF;
            if (first < 'a' || first > 'z' || (first ^ 32) != (data[at] & 0xFF)) {
                return false;
            }
            for (int each = 1; each < wordLength; each++) {
                if (words[wordAt + each] != data[at + each]) {
                    return false;
                }
            }
            return true;
        }
        for (int each = 0; each < wordLength; each++) {
            int letter = words[wordAt + each] & 0xFF;
            int wanted = letter >= 'a' && letter <= 'z' ? letter ^ 32 : letter;
            if (wanted != (data[at + each] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    static boolean findAll(byte[] data, int at, int minLength, int maxLength,
            int[] matches) {

        boolean hasFoundMatch = false;
      {
        int offset = BrotliDictionaryWords.firstWordOfBucket(hash15(data, at));
        boolean end = offset == 0;
        while (!end) {
          int wordLength = BrotliDictionaryWords.lengthAndFlagOf(offset);
          int wordTransform = BrotliDictionaryWords.transformOf(offset);
          int wordIndex = BrotliDictionaryWords.indexOf(offset);
          offset++;
          int l = wordLength & 0x1F;
          int n = 1 << BrotliDictionary.sizeBitsFor(l);
          int id = wordIndex;
          end = (wordLength & BrotliDictionaryWords.LAST_IN_ITS_BUCKET) != 0;
          wordLength = l;
          if (wordTransform == 0) {
            int matchlen =
                howMuchOfTheWordMatches(data, at, id, l, maxLength);
            int minlen;
            int maxlen;
            int len;

            if (matchlen == l) {
              addMatch(matches, id, l, l);
              hasFoundMatch = true;
            }

            if (matchlen >= l - 1) {
              addMatch(matches, id + 12 * n, l - 1, l);
              if (l + 2 < maxLength &&
                  data[at + l - 1] == 'i' && data[at + l] == 'n' && data[at + l + 1] == 'g' &&
                  data[at + l + 2] == ' ') {
                addMatch(matches, id + 49 * n, l + 3, l);
              }
              hasFoundMatch = true;
            }

            minlen = minLength;
            if (l > 9) minlen = Math.max(minlen, l - 9);
            maxlen = Math.min(matchlen, l - 2);
            for (len = minlen; len <= maxlen; ++len) {
              int cut = l - len;
              int whichTransform = (cut << 2) +
                  (int) ((CUTTING_TRANSFORM_FOR_EACH_AMOUNT >>> (cut * 6)) & 0x3F);
              addMatch(matches, id + whichTransform * n, len, l);
              hasFoundMatch = true;
            }
            if (matchlen < l || l + 6 >= maxLength) {
              continue;
            }

            if (data[at + (l)] == ' ') {
              addMatch(matches, id + n, l + 1, l);
              if (data[at + (l) + 1] == 'a') {
                if (data[at + (l) + 2] == ' ') {
                  addMatch(matches, id + 28 * n, l + 3, l);
                } else if (data[at + (l) + 2] == 's') {
                  if (data[at + (l) + 3] == ' ') addMatch(matches, id + 46 * n, l + 4, l);
                } else if (data[at + (l) + 2] == 't') {
                  if (data[at + (l) + 3] == ' ') addMatch(matches, id + 60 * n, l + 4, l);
                } else if (data[at + (l) + 2] == 'n') {
                  if (data[at + (l) + 3] == 'd' && data[at + (l) + 4] == ' ') {
                    addMatch(matches, id + 10 * n, l + 5, l);
                  }
                }
              } else if (data[at + (l) + 1] == 'b') {
                if (data[at + (l) + 2] == 'y' && data[at + (l) + 3] == ' ') {
                  addMatch(matches, id + 38 * n, l + 4, l);
                }
              } else if (data[at + (l) + 1] == 'i') {
                if (data[at + (l) + 2] == 'n') {
                  if (data[at + (l) + 3] == ' ') addMatch(matches, id + 16 * n, l + 4, l);
                } else if (data[at + (l) + 2] == 's') {
                  if (data[at + (l) + 3] == ' ') addMatch(matches, id + 47 * n, l + 4, l);
                }
              } else if (data[at + (l) + 1] == 'f') {
                if (data[at + (l) + 2] == 'o') {
                  if (data[at + (l) + 3] == 'r' && data[at + (l) + 4] == ' ') {
                    addMatch(matches, id + 25 * n, l + 5, l);
                  }
                } else if (data[at + (l) + 2] == 'r') {
                  if (data[at + (l) + 3] == 'o' && data[at + (l) + 4] == 'm' && data[at + (l) + 5] == ' ') {
                    addMatch(matches, id + 37 * n, l + 6, l);
                  }
                }
              } else if (data[at + (l) + 1] == 'o') {
                if (data[at + (l) + 2] == 'f') {
                  if (data[at + (l) + 3] == ' ') addMatch(matches, id + 8 * n, l + 4, l);
                } else if (data[at + (l) + 2] == 'n') {
                  if (data[at + (l) + 3] == ' ') addMatch(matches, id + 45 * n, l + 4, l);
                }
              } else if (data[at + (l) + 1] == 'n') {
                if (data[at + (l) + 2] == 'o' && data[at + (l) + 3] == 't' && data[at + (l) + 4] == ' ') {
                  addMatch(matches, id + 80 * n, l + 5, l);
                }
              } else if (data[at + (l) + 1] == 't') {
                if (data[at + (l) + 2] == 'h') {
                  if (data[at + (l) + 3] == 'e') {
                    if (data[at + (l) + 4] == ' ') addMatch(matches, id + 5 * n, l + 5, l);
                  } else if (data[at + (l) + 3] == 'a') {
                    if (data[at + (l) + 4] == 't' && data[at + (l) + 5] == ' ') {
                      addMatch(matches, id + 29 * n, l + 6, l);
                    }
                  }
                } else if (data[at + (l) + 2] == 'o') {
                  if (data[at + (l) + 3] == ' ') addMatch(matches, id + 17 * n, l + 4, l);
                }
              } else if (data[at + (l) + 1] == 'w') {
                if (data[at + (l) + 2] == 'i' && data[at + (l) + 3] == 't' && data[at + (l) + 4] == 'h' && data[at + (l) + 5] == ' ') {
                  addMatch(matches, id + 35 * n, l + 6, l);
                }
              }
            } else if (data[at + (l)] == '"') {
              addMatch(matches, id + 19 * n, l + 1, l);
              if (data[at + (l) + 1] == '>') {
                addMatch(matches, id + 21 * n, l + 2, l);
              }
            } else if (data[at + (l)] == '.') {
              addMatch(matches, id + 20 * n, l + 1, l);
              if (data[at + (l) + 1] == ' ') {
                addMatch(matches, id + 31 * n, l + 2, l);
                if (data[at + (l) + 2] == 'T' && data[at + (l) + 3] == 'h') {
                  if (data[at + (l) + 4] == 'e') {
                    if (data[at + (l) + 5] == ' ') addMatch(matches, id + 43 * n, l + 6, l);
                  } else if (data[at + (l) + 4] == 'i') {
                    if (data[at + (l) + 5] == 's' && data[at + (l) + 6] == ' ') {
                      addMatch(matches, id + 75 * n, l + 7, l);
                    }
                  }
                }
              }
            } else if (data[at + (l)] == ',') {
              addMatch(matches, id + 76 * n, l + 1, l);
              if (data[at + (l) + 1] == ' ') {
                addMatch(matches, id + 14 * n, l + 2, l);
              }
            } else if (data[at + (l)] == '\n') {
              addMatch(matches, id + 22 * n, l + 1, l);
              if (data[at + (l) + 1] == '\t') {
                addMatch(matches, id + 50 * n, l + 2, l);
              }
            } else if (data[at + (l)] == ']') {
              addMatch(matches, id + 24 * n, l + 1, l);
            } else if (data[at + (l)] == '\'') {
              addMatch(matches, id + 36 * n, l + 1, l);
            } else if (data[at + (l)] == ':') {
              addMatch(matches, id + 51 * n, l + 1, l);
            } else if (data[at + (l)] == '(') {
              addMatch(matches, id + 57 * n, l + 1, l);
            } else if (data[at + (l)] == '=') {
              if (data[at + (l) + 1] == '"') {
                addMatch(matches, id + 70 * n, l + 2, l);
              } else if (data[at + (l) + 1] == '\'') {
                addMatch(matches, id + 86 * n, l + 2, l);
              }
            } else if (data[at + (l)] == 'a') {
              if (data[at + (l) + 1] == 'l' && data[at + (l) + 2] == ' ') {
                addMatch(matches, id + 84 * n, l + 3, l);
              }
            } else if (data[at + (l)] == 'e') {
              if (data[at + (l) + 1] == 'd') {
                if (data[at + (l) + 2] == ' ') addMatch(matches, id + 53 * n, l + 3, l);
              } else if (data[at + (l) + 1] == 'r') {
                if (data[at + (l) + 2] == ' ') addMatch(matches, id + 82 * n, l + 3, l);
              } else if (data[at + (l) + 1] == 's') {
                if (data[at + (l) + 2] == 't' && data[at + (l) + 3] == ' ') {
                  addMatch(matches, id + 95 * n, l + 4, l);
                }
              }
            } else if (data[at + (l)] == 'f') {
              if (data[at + (l) + 1] == 'u' && data[at + (l) + 2] == 'l' && data[at + (l) + 3] == ' ') {
                addMatch(matches, id + 90 * n, l + 4, l);
              }
            } else if (data[at + (l)] == 'i') {
              if (data[at + (l) + 1] == 'v') {
                if (data[at + (l) + 2] == 'e' && data[at + (l) + 3] == ' ') {
                  addMatch(matches, id + 92 * n, l + 4, l);
                }
              } else if (data[at + (l) + 1] == 'z') {
                if (data[at + (l) + 2] == 'e' && data[at + (l) + 3] == ' ') {
                  addMatch(matches, id + 100 * n, l + 4, l);
                }
              }
            } else if (data[at + (l)] == 'l') {
              if (data[at + (l) + 1] == 'e') {
                if (data[at + (l) + 2] == 's' && data[at + (l) + 3] == 's' && data[at + (l) + 4] == ' ') {
                  addMatch(matches, id + 93 * n, l + 5, l);
                }
              } else if (data[at + (l) + 1] == 'y') {
                if (data[at + (l) + 2] == ' ') addMatch(matches, id + 61 * n, l + 3, l);
              }
            } else if (data[at + (l)] == 'o') {
              if (data[at + (l) + 1] == 'u' && data[at + (l) + 2] == 's' && data[at + (l) + 3] == ' ') {
                addMatch(matches, id + 106 * n, l + 4, l);
              }
            }
          } else {

            boolean isAllCaps =
                wordTransform != UPPERCASE_FIRST;
            if (!wordMatches(data, at, wordLength, wordTransform, wordIndex, maxLength)) {
              continue;
            }

            addMatch(matches, id + (isAllCaps ? 44 : 9) * n, l, l);
            hasFoundMatch = true;
            if (l + 1 >= maxLength) {
              continue;
            }

            if (data[at + (l)] == ' ') {
              addMatch(matches, id + (isAllCaps ? 68 : 4) * n, l + 1, l);
            } else if (data[at + (l)] == '"') {
              addMatch(matches, id + (isAllCaps ? 87 : 66) * n, l + 1, l);
              if (data[at + (l) + 1] == '>') {
                addMatch(matches, id + (isAllCaps ? 97 : 69) * n, l + 2, l);
              }
            } else if (data[at + (l)] == '.') {
              addMatch(matches, id + (isAllCaps ? 101 : 79) * n, l + 1, l);
              if (data[at + (l) + 1] == ' ') {
                addMatch(matches, id + (isAllCaps ? 114 : 88) * n, l + 2, l);
              }
            } else if (data[at + (l)] == ',') {
              addMatch(matches, id + (isAllCaps ? 112 : 99) * n, l + 1, l);
              if (data[at + (l) + 1] == ' ') {
                addMatch(matches, id + (isAllCaps ? 107 : 58) * n, l + 2, l);
              }
            } else if (data[at + (l)] == '\'') {
              addMatch(matches, id + (isAllCaps ? 94 : 74) * n, l + 1, l);
            } else if (data[at + (l)] == '(') {
              addMatch(matches, id + (isAllCaps ? 113 : 78) * n, l + 1, l);
            } else if (data[at + (l)] == '=') {
              if (data[at + (l) + 1] == '"') {
                addMatch(matches, id + (isAllCaps ? 105 : 104) * n, l + 2, l);
              } else if (data[at + (l) + 1] == '\'') {
                addMatch(matches, id + (isAllCaps ? 116 : 108) * n, l + 2, l);
              }
            }
          }
        }
      }

      if (maxLength >= 5 && (data[at] == ' ' || data[at] == '.')) {
        boolean isSpace = data[at] == ' ';
        int offset = BrotliDictionaryWords.firstWordOfBucket(hash15(data, at + 1));
        boolean end = offset == 0;
        while (!end) {
          int wordLength = BrotliDictionaryWords.lengthAndFlagOf(offset);
          int wordTransform = BrotliDictionaryWords.transformOf(offset);
          int wordIndex = BrotliDictionaryWords.indexOf(offset);
          offset++;
          int l = wordLength & 0x1F;
          int n = 1 << BrotliDictionary.sizeBitsFor(l);
          int id = wordIndex;
          end = (wordLength & BrotliDictionaryWords.LAST_IN_ITS_BUCKET) != 0;
          wordLength = l;
          if (wordTransform == 0) {
            if (!wordMatches(data, at + 1, wordLength, wordTransform, wordIndex, maxLength - 1)) {
              continue;
            }

            addMatch(matches, id + (isSpace ? 6 : 32) * n, l + 1, l);
            hasFoundMatch = true;
            if (l + 2 >= maxLength) {
              continue;
            }

            if (data[at + (l + 1)] == ' ') {
              addMatch(matches, id + (isSpace ? 2 : 77) * n, l + 2, l);
            } else if (data[at + (l + 1)] == '(') {
              addMatch(matches, id + (isSpace ? 89 : 67) * n, l + 2, l);
            } else if (isSpace) {
              if (data[at + (l + 1)] == ',') {
                addMatch(matches, id + 103 * n, l + 2, l);
                if (data[at + (l + 1) + 1] == ' ') {
                  addMatch(matches, id + 33 * n, l + 3, l);
                }
              } else if (data[at + (l + 1)] == '.') {
                addMatch(matches, id + 71 * n, l + 2, l);
                if (data[at + (l + 1) + 1] == ' ') {
                  addMatch(matches, id + 52 * n, l + 3, l);
                }
              } else if (data[at + (l + 1)] == '=') {
                if (data[at + (l + 1) + 1] == '"') {
                  addMatch(matches, id + 81 * n, l + 3, l);
                } else if (data[at + (l + 1) + 1] == '\'') {
                  addMatch(matches, id + 98 * n, l + 3, l);
                }
              }
            }
          } else if (isSpace) {

            boolean isAllCaps =
                wordTransform != UPPERCASE_FIRST;
            if (!wordMatches(data, at + 1, wordLength, wordTransform, wordIndex, maxLength - 1)) {
              continue;
            }

            addMatch(matches, id + (isAllCaps ? 85 : 30) * n, l + 1, l);
            hasFoundMatch = true;
            if (l + 2 >= maxLength) {
              continue;
            }

            if (data[at + (l + 1)] == ' ') {
              addMatch(matches, id + (isAllCaps ? 83 : 15) * n, l + 2, l);
            } else if (data[at + (l + 1)] == ',') {
              if (!isAllCaps) {
                addMatch(matches, id + 109 * n, l + 2, l);
              }
              if (data[at + (l + 1) + 1] == ' ') {
                addMatch(matches, id + (isAllCaps ? 111 : 65) * n, l + 3, l);
              }
            } else if (data[at + (l + 1)] == '.') {
              addMatch(matches, id + (isAllCaps ? 115 : 96) * n, l + 2, l);
              if (data[at + (l + 1) + 1] == ' ') {
                addMatch(matches, id + (isAllCaps ? 117 : 91) * n, l + 3, l);
              }
            } else if (data[at + (l + 1)] == '=') {
              if (data[at + (l + 1) + 1] == '"') {
                addMatch(matches, id + (isAllCaps ? 110 : 118) * n, l + 3, l);
              } else if (data[at + (l + 1) + 1] == '\'') {
                addMatch(matches, id + (isAllCaps ? 119 : 120) * n, l + 3, l);
              }
            }
          }
        }
      }
      if (maxLength >= 6) {

        if ((data[at + 1] == ' ' &&
             (data[at] == 'e' || data[at] == 's' || data[at] == ',')) ||
            ((data[at] & 0xFF) == 0xC2 && (data[at + 1] & 0xFF) == 0xA0)) {
          int offset = BrotliDictionaryWords.firstWordOfBucket(hash15(data, at + 2));
          boolean end = offset == 0;
          while (!end) {
            int wordLength = BrotliDictionaryWords.lengthAndFlagOf(offset);
          int wordTransform = BrotliDictionaryWords.transformOf(offset);
          int wordIndex = BrotliDictionaryWords.indexOf(offset);
          offset++;
            int l = wordLength & 0x1F;
            int n = 1 << BrotliDictionary.sizeBitsFor(l);
            int id = wordIndex;
            end = (wordLength & BrotliDictionaryWords.LAST_IN_ITS_BUCKET) != 0;
            wordLength = l;
            if (wordTransform == 0 &&
                wordMatches(data, at + 2, wordLength, wordTransform, wordIndex, maxLength - 2)) {
              if ((data[at] & 0xFF) == 0xC2) {
                addMatch(matches, id + 102 * n, l + 2, l);
                hasFoundMatch = true;
              } else if (l + 2 < maxLength && data[at + l + 2] == ' ') {
                int t = data[at] == 'e' ? 18 : (data[at] == 's' ? 7 : 13);
                addMatch(matches, id + t * n, l + 3, l);
                hasFoundMatch = true;
              }
            }
          }
        }
      }
      if (maxLength >= 9) {

        if ((data[at] == ' ' && data[at + 1] == 't' && data[at + 2] == 'h' &&
             data[at + 3] == 'e' && data[at + 4] == ' ') ||
            (data[at] == '.' && data[at + 1] == 'c' && data[at + 2] == 'o' &&
             data[at + 3] == 'm' && data[at + 4] == '/')) {
          int offset = BrotliDictionaryWords.firstWordOfBucket(hash15(data, at + 5));
          boolean end = offset == 0;
          while (!end) {
            int wordLength = BrotliDictionaryWords.lengthAndFlagOf(offset);
          int wordTransform = BrotliDictionaryWords.transformOf(offset);
          int wordIndex = BrotliDictionaryWords.indexOf(offset);
          offset++;
            int l = wordLength & 0x1F;
            int n = 1 << BrotliDictionary.sizeBitsFor(l);
            int id = wordIndex;
            end = (wordLength & BrotliDictionaryWords.LAST_IN_ITS_BUCKET) != 0;
            wordLength = l;
            if (wordTransform == 0 &&
                wordMatches(data, at + 5, wordLength, wordTransform, wordIndex, maxLength - 5)) {
              addMatch(matches, id + (data[at] == ' ' ? 41 : 72) * n, l + 5, l);
              hasFoundMatch = true;
              if (l + 5 < maxLength) {
                if (data[at] == ' ') {
                  if (l + 8 < maxLength &&
                      data[at + (l + 5)] == ' ' && data[at + (l + 5) + 1] == 'o' && data[at + (l + 5) + 2] == 'f' && data[at + (l + 5) + 3] == ' ') {
                    addMatch(matches, id + 62 * n, l + 9, l);
                    if (l + 12 < maxLength &&
                        data[at + (l + 5) + 4] == 't' && data[at + (l + 5) + 5] == 'h' && data[at + (l + 5) + 6] == 'e' && data[at + (l + 5) + 7] == ' ') {
                      addMatch(matches, id + 73 * n, l + 13, l);
                    }
                  }
                }
              }
            }
          }
        }
      }
        return hasFoundMatch;
    }
}
