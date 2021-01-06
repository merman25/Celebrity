#!/usr/bin/bash

print_usage() {
    printf "USAGE: $0 [-hfjr] [-u URL]\n"
    printf "\n"
    printf "\t-h:\t\tPrint this message and exit\n"
    printf "\t-f:\t\tFast mode (default off)\n"
    printf "\t-j:\t\tRun from jar (default off)\n"
    printf "\t-r:\t\tInclude tests of restored games (default off)\n"
    printf "\t-u URL:\t\tSpecify URL to use\n"
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

exec_command_in_new_window() {
    if is_cygwin; then
	shift 1 # ignore title, don't know how to set it
	cygstart C:/cygwin64/bin/mintty.exe -i /Cygwin.ico "$*"
    else
	title="$1"
	shift 1
	xterm -T "$title" -e "$*"
    fi
}

exec_command_in_new_max_window() {
    if is_cygwin; then
	# Don't know how to start cygwin maximised
	shift 1 # ignore title, don't know how to set it
	cygstart C:/cygwin64/bin/mintty.exe -i /Cygwin.ico "$*"
    else
	title="$1"
	shift 1
	xterm -maximized -T "$title" -e "$*"
    fi
}


FAST_MODE="false"
FROM_JAR="false"
INC_RESTORED="false"
while getopts "hfjru:" OPT; do
    case $OPT in
	h)
	    print_usage
	    exit 0
	    ;;
	f)
	    FAST_MODE="true"
	    ;;
	j)
	    FROM_JAR="true"
	    ;;
	r)
	    INC_RESTORED="true"
	    ;;
	u)
	    URL="$OPTARG"
	    ;;
	*)
	    print_usage
	    exit 1
	    ;;
    esac
done

if is_cygwin; then
    exe_name='xxx'
    ppid=$(cat /proc/self/ppid)
    while [ $exe_name != '/usr/bin/mintty' ]; do
	ppid=$(cat /proc/$ppid/ppid)
	exe_name=$(ls -l /proc/$ppid/exe | gawk '{print $NF}')
    done

    sleep 5
    ps | grep mintty | gawk '$1!= '$ppid' {print $1}' | xargs kill 2>/dev/null
else
    ps -e | grep xterm | sed 's/^ *//' | cut -d' ' -f1 | xargs kill 2>/dev/null
fi

if [ "$URL" == "" ]; then
    if [ "$FROM_JAR" == "true" ]; then
	server_command='java -Xmx256m -jar celebrity.jar'
    else
	if is_cygwin; then
	    CLASSPATH='bin;lib/json-20190722.jar'
	else
	    CLASSPATH='bin:lib/json-20190722.jar'
	fi
	
	server_command="java -Xmx256m -cp $CLASSPATH com.merman.celebrity.server.CelebrityMain"
    fi

    exec_command_in_new_max_window Server "$server_command" --create-files false --delete-existing false --logging sysout test_games/1000/11 test_games/1001/14 &
fi

sleep 1
cd client
rm -fr cypress/screenshots/*
rm -fr cypress/videos/*


test_type="full"
if [ "$FAST_MODE" == "true" ]; then
    test_type="fast"
fi

if [ ! -d results-fast ]; then
    mkdir results-fast
fi
if [ ! -d results-full ]; then
    mkdir results-full
fi
rm -f "results-$test_type"/*

if is_cygwin; then
    OS="win"
else
    OS="linux"
fi

exec_command_in_new_window 'Player 1' npx cypress run -s cypress/integration/celebrity-tests.js --env PLAYER_INDEX=0,FAST_MODE=$FAST_MODE,URL=$URL,INC_RESTORED=$INC_RESTORED,OS=$OS --headed -p 10000 '>' "results-$test_type/player1-report" &
exec_command_in_new_window 'Player 2' npx cypress run -s cypress/integration/celebrity-tests.js --env PLAYER_INDEX=1,FAST_MODE=$FAST_MODE,URL=$URL,INC_RESTORED=$INC_RESTORED,OS=$OS --headed -p 10001 '>' "results-$test_type/player2-report" &
exec_command_in_new_window 'Player 3' npx cypress run -s cypress/integration/celebrity-tests.js --env PLAYER_INDEX=2,FAST_MODE=$FAST_MODE,URL=$URL,INC_RESTORED=$INC_RESTORED,OS=$OS --headed -p 10002 '>' "results-$test_type/player3-report" &
exec_command_in_new_window 'Player 4' npx cypress run -s cypress/integration/celebrity-tests.js --env PLAYER_INDEX=3,FAST_MODE=$FAST_MODE,URL=$URL,INC_RESTORED=$INC_RESTORED,OS=$OS --headed -p 10003 '>' "results-$test_type/player4-report" &

sleep 5
exec_command_in_new_window Dashboard bash dashboard.sh &


while sleep 1; do
    num_passes=$(grep -l 'All specs passed' "results-$test_type"/player* | wc -l)
    if [ $num_passes -eq 4 ]; then
	echo "OK"
	exit 0
    else
	num_fails=$(grep -l fail "results-$test_type"/* | wc -l);
	if [ $num_fails -gt 0 ]; then
	    echo "FAIL"
	    exit $num_fails
	fi
    fi
done
    
