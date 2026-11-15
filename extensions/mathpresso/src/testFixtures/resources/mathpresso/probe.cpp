#include "mathpresso/mathpresso.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <string>
#include <vector>

using namespace mathpresso;

static std::string logged;

struct ExtensionLog : public OutputLog {
  void log(unsigned int type, unsigned int, unsigned int, const char* message, size_t) override {
    logged += (type == kMessageError) ? "ERROR: " : "WARNING: ";
    for (const char* p = message; *p; p++) {
      if (*p == '\n') logged += "\\n";
      else if (*p == '\t') logged += "\\t";
      else logged += *p;
    }
    logged += "\\n";
  }
};

static double parseDouble(const std::string& s) {
  uint64_t u = strtoull(s.c_str(), nullptr, 16);
  double d;
  memcpy(&d, &u, 8);
  return d;
}

static std::string hexOf(double d) {
  uint64_t u;
  memcpy(&u, &d, 8);
  char buffer[17];
  snprintf(buffer, sizeof buffer, "%016llx", (unsigned long long)u);
  return buffer;
}

static std::vector<std::string> split(const std::string& s, char by) {
  std::vector<std::string> parts;
  size_t start = 0;
  while (start <= s.size()) {
    size_t end = s.find(by, start);
    if (end == std::string::npos) end = s.size();
    parts.push_back(s.substr(start, end - start));
    start = end + 1;
  }
  return parts;
}

static std::string unescape(const std::string& s) {
  std::string out;
  for (size_t i = 0; i < s.size(); i++) {
    if (s[i] == '\\' && i + 1 < s.size()) {
      char c = s[++i];
      out += c == 'n' ? '\n' : c == 't' ? '\t' : c;
    } else out += s[i];
  }
  return out;
}

int main() {
  static char buffer[1 << 20];
  while (fgets(buffer, sizeof buffer, stdin)) {
    std::string line(buffer);
    while (!line.empty() && (line.back() == '\n' || line.back() == '\r')) line.pop_back();
    std::vector<std::string> fields = split(line, '\t');
    if (fields.size() != 3) { printf("BAD-INPUT\n"); fflush(stdout); continue; }

    Context context;
    context.addBuiltIns();
    std::vector<std::string> names;
    for (const std::string& name : split(fields[0], ' ')) if (!name.empty()) names.push_back(name);
    for (size_t i = 0; i < names.size(); i++)
      context.addVariable(names[i].c_str(), int(i * sizeof(double)));

    std::vector<double> data;
    for (const std::string& value : split(fields[2], ' ')) if (!value.empty()) data.push_back(parseDouble(value));
    if (data.size() < names.size()) data.resize(names.size(), 0.0);

    logged.clear();
    ExtensionLog log;
    Expression expression;
    std::string body = unescape(fields[1]);
    Error error = expression.compile(context, body.c_str(), kOptionVerbose, &log);

    if (error != kErrorOk) {
      printf("none\t%u\t\t%s\n", error, logged.c_str());
    } else {
      double result = expression.evaluate(data.data());
      std::string after;
      for (size_t i = 0; i < data.size(); i++) { if (i) after += ' '; after += hexOf(data[i]); }
      printf("%s\t0\t%s\t%s\n", hexOf(result).c_str(), after.c_str(), logged.c_str());
    }
    fflush(stdout);
  }
  return 0;
}
