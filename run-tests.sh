#!/usr/bin/bash

print_usage() {
    printf "USAGE: $0 [-fhjrswx] [-u URL] [-d SEED]\n"
    printf "\n"
    printf "\t-h:\t\tPrint this message and exit\n"
    printf "\t-f:\t\tFast mode (default off)\n"
    printf "\t-j:\t\tRun from jar (default off)\n"
    printf "\t-q:\t\tPlay a random game (default off)\n"
    printf "\t-r:\t\tInclude tests of restored games (default off)\n"
    printf "\t-s:\t\tServer already running, don't start a new one (default off, so new server instance will be started)\n"
    printf "\t-w:\t\tOpen browser windows (default off)\n"
    printf "\t-x:\t\tExit browser at end of test (default off)\n"
    printf "\t-d SEED:\tSpecify seed for random game\n"
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
HEAD="--headless"
EXIT="--no-exit"
START_SERVER="true"
RANDOM_GAME="false"
SEED=""
while getopts "hfjqrswxu:d:" OPT; do
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
	q)
	    RANDOM_GAME="true"
	    ;;
	r)
	    INC_RESTORED="true"
	    ;;
	s)
	    START_SERVER="false"
	    ;;
	w)
	    HEAD="--headed"
	    ;;
	x)
	    EXIT=""
	    ;;
	d)
	    SEED="$OPTARG"
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

if [ "$RANDOM_GAME" == "true" ]; then
    if [ -z "$SEED" ]; then
	SEED=$(dd if=/dev/urandom count=4 bs=1 2>/dev/null | od -An -tx | tail -c9)
    fi
    echo "seed: $SEED"
fi

if [ "$URL" == "" ]; then
    if [ "$START_SERVER" == "true" ]; then
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

	if [ \! -z "$SEED" ]; then
	    server_command="$server_command --seed $((16#$SEED))"
	fi

	exec_command_in_new_max_window Server "$server_command" --create-files false --delete-existing false --logging sysout test_games/1000/11 test_games/1001/14 &
    fi
fi

sleep 1
cd client
rm -fr cypress/screenshots/*
rm -fr cypress/videos/*

if [ -d temp_files ]; then
    rm -fr temp_files
fi
mkdir temp_files


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

if [ "$RANDOM_GAME" == "true" ]; then
    MAX_RANDOM_INT_IN_BASH=32767 # min value is 0
    RANDOM=$((16#"$SEED" % $MAX_RANDOM_INT_IN_BASH)) # seed the generator to get predictable value
    
    num_players=$((2 + $RANDOM % 9));

    for player_index in $(seq 0 $(( $num_players - 1 )) ); do
	exec_command_in_new_window "Player $player_index" npx cypress run -s cypress/integration/celebrity-tests.js --env PLAYER_INDEX=$player_index,FAST_MODE=$FAST_MODE,URL=$URL,RANDOM=true,NUM_PLAYERS=$num_players,SEED="$SEED" $HEAD $EXIT -p $((10000 + $player_index )) '>' "results-$test_type/player${player_index}-report" &
    done
else
    num_players=4
    
    exec_command_in_new_window 'Player 1' npx cypress run -s cypress/integration/celebrity-tests.js --env PLAYER_INDEX=0,FAST_MODE=$FAST_MODE,URL=$URL,INC_RESTORED=$INC_RESTORED $HEAD $EXIT -p 10000 '>' "results-$test_type/player1-report" &
    exec_command_in_new_window 'Player 2' npx cypress run -s cypress/integration/celebrity-tests.js --env PLAYER_INDEX=1,FAST_MODE=$FAST_MODE,URL=$URL,INC_RESTORED=$INC_RESTORED $HEAD $EXIT -p 10001 '>' "results-$test_type/player2-report" &
    exec_command_in_new_window 'Player 3' npx cypress run -s cypress/integration/celebrity-tests.js --env PLAYER_INDEX=2,FAST_MODE=$FAST_MODE,URL=$URL,INC_RESTORED=$INC_RESTORED $HEAD $EXIT -p 10002 '>' "results-$test_type/player3-report" &
    exec_command_in_new_window 'Player 4' npx cypress run -s cypress/integration/celebrity-tests.js --env PLAYER_INDEX=3,FAST_MODE=$FAST_MODE,URL=$URL,INC_RESTORED=$INC_RESTORED $HEAD $EXIT -p 10003 '>' "results-$test_type/player4-report" &
fi

sleep 5
exec_command_in_new_window Dashboard bash dashboard.sh &


while sleep 1; do
    num_passes=$(grep -l 'All specs passed' "results-$test_type"/player* | wc -l)
    if [ $num_passes -eq $num_players ]; then
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
    
