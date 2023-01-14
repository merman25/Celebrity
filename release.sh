#!/bin/bash

ee() {
    echo -e "\n=== $1 ===\n"
    eval "$1"
}

is_cygwin(){
	UNAME_OUTPUT=$(uname -a)
	CYGWIN_REGEX=".*Cygwin.*"
	if [[ "${UNAME_OUTPUT}" =~ ${CYGWIN_REGEX} ]]; then
		return 0
	else
		return 1
	fi
}

exec_gsutil_command() {
    if is_cygwin; then
	ee "cmd /c $*"
    else
	ee "$*"
    fi
}

upload() {
    ee 'rm -fr upload' \
	&& exec_gsutil_command gsutil -m rm -r gs://merman-celebrity/celebrity \
	&& ee 'mkdir upload' \
	&& ee 'cp -r --parents celebrity.jar lib/json-20190722.jar lorem-ipsum.txt client/{celebrity.html,styles.css,icons,js} upload' \
	&& exec_gsutil_command gsutil -m cp -r upload gs://merman-celebrity/celebrity \
	&& ee 'rm -fr upload'
}

fix_symlink_on_windows() {
    # quick hack
    if is_cygwin; then
	ee '/bin/rm client/cypress/integration/util.js'
	ee '/bin/cp client/js/util.js client/cypress/integration'
    fi
}

ee 'rm -fr release' \
    && ee 'mkdir release' \
    && ee 'cd release' \
    && ee 'git clone https://github.com/merman25/celebrity' \
    && ee 'cd celebrity' \
    && ee 'ant build' \
    && ee 'cd client' \
    && ee 'npm install' \
    && ee 'cd ..' \
    && ee 'fix_symlink_on_windows' \
    && ee './client/node_modules/mocha/bin/_mocha'
    && ee 'bash run-tests.sh -fjkx' \
    && ee 'bash run-tests.sh -srwx' \
    && ee 'upload' \
    && ee 'cd ..' \
    && echo -e '\n\nrelease UPLOADED\n' \
