#!/usr/bin/env bash
#
# Prints a Markdown digest of a JUnit XML directory on stdout: totals, then every failing test
# with its class, name and failure message.
#
#     bash .github/scripts/junit-summary.sh backend/build/test-results/test
#
# WHY THIS EXISTS (#599)
#
# The account's Actions artifact storage quota is exhausted, so `actions/upload-artifact` fails
# with "Artifact storage quota has been hit. Unable to upload any new artifacts." The consequence
# was not just a red step — it was that **no test report was readable from a run at all**. The
# only way to find out which tests had failed was to read the XML off the self-hosted runner's
# working directory by hand, which is a usability failure at exactly the moment CI matters.
#
# Retention limits and narrower upload paths reduce how fast the quota refills, but they do not
# help on a day when it is already full: the upload still fails and the report is still
# unreadable. The step summary is not subject to the artifact quota at all, so writing the digest
# there makes the answer available whatever the storage account is doing.
#
# It also covers a gap the artifacts never did. `mikepenz/action-junit-report` publishes a check
# run, but only on `pull_request` — a push to `main`, which is what gates the deploy, had no
# readable report even when uploads worked.
#
# WHY SHELL AND NOT AN ACTION
#
# The same reason `paths-changed.sh` is shell: the self-hosted runner's action cache is seeded by
# hand (`infra/scripts/seed-runner-action-cache.sh`) so that jobs stop downloading from codeload,
# which returned 429 for a whole day and killed every job in *Set up job* (#563). Every new action
# is one more entry to keep seeded. awk is already installed.
#
# There is ONE self-hosted runner and it is the production host, so cost matters (#722): this
# reads a few dozen small XML files with awk and finishes in well under a second. It deliberately
# does not parse the 465-file generated HTML report, which is the thing that was being uploaded.
#
# Exit status is always 0. This step reports, it does not gate — the test step itself is the gate,
# and a summary that could fail the job would just be a new way to go red on something that is not
# the code.
set -uo pipefail

RESULTS_DIR="${1:-backend/build/test-results/test}"

if [ ! -d "$RESULTS_DIR" ]; then
    echo "No test results directory at \`$RESULTS_DIR\` — the suite did not run."
    exit 0
fi

# `find` rather than a glob: an empty glob expands to the literal pattern and awk would then
# report a missing file as an error.
XML_FILES="$(find "$RESULTS_DIR" -name '*.xml' -type f 2>/dev/null | sort)"

if [ -z "$XML_FILES" ]; then
    echo "No JUnit XML under \`$RESULTS_DIR\` — the suite did not run."
    exit 0
fi

# One awk pass over every file.
#
# Gradle writes <testsuite ...> with the totals as attributes, then one <testcase ...> per test.
# A passing case is self-closing; a failing one wraps a <failure> or <error> child. So: remember
# the most recent testcase's classname and name, and when a failure/error tag turns up, attribute
# it to that case. Attribute order is not guaranteed, hence the per-attribute matches rather than
# one big regex over the line.
#
# `msg` is truncated: a Kotlin assertion message can carry a multi-line diff, and a summary that
# reproduces a whole stack trace per failure is as unreadable as no summary. The full text is in
# the XML, which is what the (now much smaller) artifact carries.
# shellcheck disable=SC2016  # the awk program is deliberately single-quoted: `$0` is awk's
# current record, not a shell variable, and must reach awk unexpanded.
echo "$XML_FILES" | tr '\n' '\0' | xargs -0 awk -v results_dir="$RESULTS_DIR" '
function attr(line, key,   s, v) {
    if (match(line, key "=\"[^\"]*\"")) {
        v = substr(line, RSTART + length(key) + 2, RLENGTH - length(key) - 3)
        return v
    }
    return ""
}
function unescape(s) {
    gsub(/&lt;/, "<", s); gsub(/&gt;/, ">", s)
    gsub(/&quot;/, "\"", s); gsub(/&apos;/, "\047", s)   # octal, so the awk program needs no embedded shell quote
    gsub(/&#10;/, " "); gsub(/&#13;/, " ")
    gsub(/&amp;/, "\\&", s)
    return s
}
/<testsuite / {
    tests    += attr($0, "tests") + 0
    failures += attr($0, "failures") + 0
    errors   += attr($0, "errors") + 0
    skipped  += attr($0, "skipped") + 0
}
/<testcase / {
    cls  = attr($0, "classname")
    name = attr($0, "name")
}
/<(failure|error)[ >]/ {
    kind = ($0 ~ /<error[ >]/) ? "error" : "failure"
    msg  = unescape(attr($0, "message"))
    if (msg == "") msg = unescape(attr($0, "type"))
    if (length(msg) > 300) msg = substr(msg, 1, 300) "…"
    # A raw pipe anywhere in the row splits the Markdown table into extra columns. Test names
    # here really do contain them (`should reject a pipe | in the body`), so escape the name and
    # the class as well as the message, not just the message.
    # Names get the same XML unescaping as messages: a Kotlin backtick test name routinely
    # contains an apostrophe or angle brackets, and `it&apos;s broken` in the report is just a
    # second bug to explain.
    esc_name = unescape(name); esc_cls = unescape(cls)
    gsub(/\|/, "\\|", msg); gsub(/\|/, "\\|", esc_name); gsub(/\|/, "\\|", esc_cls)
    n++
    fail_cls[n] = esc_cls; fail_name[n] = esc_name; fail_kind[n] = kind; fail_msg[n] = msg
}
END {
    passed = tests - failures - errors - skipped
    if (failures + errors == 0) {
        printf "### ✅ %d tests passed", tests
        if (skipped > 0) printf " (%d skipped)", skipped
        printf "\n"
        exit 0
    }
    printf "### ❌ %d of %d tests failed\n\n", failures + errors, tests
    printf "%d passed · %d failed · %d errors · %d skipped\n\n", passed, failures, errors, skipped
    print  "| Test | Message |"
    print  "| --- | --- |"
    for (i = 1; i <= n; i++) {
        short = fail_cls[i]
        sub(/^.*\./, "", short)     # com.muhabbet.auth.FooTest -> FooTest
        printf "| `%s.%s`%s | %s |\n", short, fail_name[i],
               (fail_kind[i] == "error" ? " ⚠️" : ""), fail_msg[i]
    }
    printf "\nFull stack traces are in the `backend-test-results` artifact when one could be uploaded; "
    printf "otherwise they are in `%s` on the runner.\n", results_dir
}
'
