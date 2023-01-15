#!/usr/bin/bash

# Global variables in CAPS, local variables in lower case.
START_DIR=$(pwd)
TEST_ROOT="./test_results"


print_usage() {
    printf "USAGE: $0 [-fhjrswxz] [-u URL] [-d SEED] [-n NUM_NAMES_PER_PLAYER] [-l NUM_PLAYERS] [-t NUM_TEAMS] [-o NUM_ROUNDS] [-m MIN_WAIT_TIME_IN_SEC] [-M MAX_WAIT_TIME_IN_SEC] [-g STAGGERED_DELAY_IN_SEC] [-p PORT] [-b BROWSER]\n"
    printf "\n"
    printf "\t-h:\t\t\t\tPrint this message and exit\n"
    printf "\t-f:\t\t\t\tFast mode (default off)\n"
    printf "\t-j:\t\t\t\tRun from jar (default off)\n"
    printf "\t-k:\t\t\t\tKill old tests and server before starting (default off)\n"
    printf "\t-q:\t\t\t\tPlay a random game (default off)\n"
    printf "\t-r:\t\t\t\tInclude tests of restored games (default off)\n"
    printf "\t-s:\t\t\t\tServer already running, don't start a new one (default off, so new server instance will be started)\n"
    printf "\t-w:\t\t\t\tOpen browser windows (default off)\n"
    printf "\t-x:\t\t\t\tExit browser at end of test (default off)\n"
    printf "\t-z:\t\t\t\tSlow mode (default off)\n"
    printf "\t-b BROWSER:\t\t\tWhich browser to use (default electron)\n"
    printf "\t-d SEED:\t\t\tSpecify seed for random game\n"
    printf "\t-g STAGGERED_DELAY_IN_SEC:\tStaggered start of each player, with specified delay (default 0)\n"
    printf "\t-l NUM_PLAYERS:\t\t\tNumber of players\n"
    printf "\t-t NUM_TEAMS:\t\t\tNumber of teams (applies to random game only, default nothing, so Cypress will default to 2)\n"
    printf "\t-m MIN_TIME:\t\t\tMin wait time in sec (slow mode only, default 5)\n"
    printf "\t-M MAX_TIME:\t\t\tMax wait time in sec (slow mode only, default 20)\n"
    printf "\t-n NUM_NAMES:\t\t\tNumber of names per player\n"
    printf "\t-o NUM_ROUNDS:\t\t\tNumber of rounds\n"
    printf "\t-p PORT:\t\t\tSpecify lowest port to use (default 10000)\n"
    printf "\t-u URL:\t\t\t\tSpecify URL to use\n"
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

fix_cygwin_path() {
    if is_cygwin; then
	cygpath -w "$*" | sed 's-\\-/-g'
    else
	echo "$*"
    fi
}

random_hex() {
    dd if=/dev/urandom count=4 bs=1 2>/dev/null | od -An -tx | tail -c9
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
	shift 1 # ignore title, don't know how to set it
	cygstart --maximize C:/cygwin64/bin/mintty.exe -i /Cygwin.ico "$*"
    else
	title="$1"
	shift 1
	xterm -maximized -T "$title" -e "$*"
    fi
}

kill_tests() {
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
}

start_server() {
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
    
    exec_command_in_new_max_window Server "$server_command" --create-files false --delete-existing false --logging sysout test_games/1000/11 test_games/1001/14  "|" "tee" "$TEST_DIR/server-logs.txt" &
}

append() {
    if [ -z "$1" ]; then
	echo -n "$2";
    else
	printf "$1","$2"
    fi
}

append_if_set() {
    env="$1"
    name="$2"
    val="$3"

    if [ \! -z "$val" ]; then
	env=$(append "$env" "$name=$val")
    fi

    echo -n "$env"
}

build_cypress_env_common() {
    player_index="$1"
    fast_mode="$2"

    TEMP_FILE_DIR=$(fix_cygwin_path "$TEST_DIR")/temp_files
    ENV="TEMP_DIR=$TEMP_FILE_DIR,PLAYER_INDEX=$player_index,FAST_MODE=$fast_mode"
    ENV=$(append_if_set "$ENV" "URL" "$URL")
    ENV=$(append_if_set "$ENV" "SLOW_MODE" "$SLOW_MODE")
    ENV=$(append_if_set "$ENV" "MIN_WAIT_TIME_IN_SEC" "$MIN_WAIT_TIME_IN_SEC")
    ENV=$(append_if_set "$ENV" "MAX_WAIT_TIME_IN_SEC" "$MAX_WAIT_TIME_IN_SEC")

    echo -n "$ENV"
}

start_player() {
    mode="$1"
    player_index="$2"
    fast_mode="$3"

    ENV=$(build_cypress_env_common "$player_index" "$fast_mode")
    if [ "$mode" == "det" ]; then
	inc_restored="$4"

	ENV=$(append_if_set "$ENV" "INC_RESTORED" "$inc_restored")
	port=$(($PORT_BASE + "$player_index"))
	result_file="$RESULTS_DIR/player${player_index}-report"
	
	exec_command_in_new_window "Player $(($player_index + 1))" npx cypress run -s cypress/integration/celebrity-tests.js --env "$ENV" $HEAD $EXIT "$BROWSER_STRING" -p $port '>' "$result_file"  &
    elif [ "$mode" == "rand" ]; then
	num_players="$4"
	seed="$5"

	ENV=$(append_if_set "$ENV" "RANDOM" "true")
	ENV=$(append_if_set "$ENV" "NUM_PLAYERS" "$num_players")
	ENV=$(append_if_set "$ENV" "NUM_TEAMS" "$NUM_TEAMS")
	ENV=$(append_if_set "$ENV" "SEED" "$seed")
	ENV=$(append_if_set "$ENV" "NUM_NAMES_PER_PLAYER" "$NUM_NAMES_PER_PLAYER")
	ENV=$(append_if_set "$ENV" "NUM_ROUNDS" "$NUM_ROUNDS")

	port=$(($PORT_BASE + "$player_index"))
	result_file="$RESULTS_DIR/player${player_index}-report"
	
	exec_command_in_new_window "Player $(($player_index + 1))" npx cypress run -s cypress/integration/celebrity-tests.js --env "$ENV" $HEAD $EXIT "$BROWSER_STRING" -p $port '>' "$result_file" &
    else
	echo "Error: unknown mode $mode"
	print_usage
	exit 1
    fi
}

# Process options
KILL_TESTS="false"
FAST_MODE="false"
FROM_JAR="false"
INC_RESTORED="false"
HEAD="--headless"
EXIT="--no-exit"
START_SERVER="true"
RANDOM_GAME="false"
SEED=""
PORT_BASE=10000
while getopts "hfjkqrswxzb:d:g:l:t:m:M:n:o:p:u:" OPT; do
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
	k)
	    KILL_TESTS="true"
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
	z)
	    SLOW_MODE="true"
	    ;;
	b)
	    BROWSER="$OPTARG"
	    ;;
	d)
	    SEED="$OPTARG"
	    ;;
	g)
	    STAGGERED_DELAY_IN_SEC="$OPTARG"
	    ;;
	l)
	    NUM_PLAYERS="$OPTARG"
	    ;;
	t)
	    NUM_TEAMS="$OPTARG"
	    ;;
	m)
	    MIN_WAIT_TIME_IN_SEC="$OPTARG"
	    ;;
	M)
	    MAX_WAIT_TIME_IN_SEC="$OPTARG"
	    ;;
	n)
	    NUM_NAMES_PER_PLAYER="$OPTARG"
	    ;;
	o)
	    NUM_ROUNDS="$OPTARG"
	    ;;
	p)
	    PORT_BASE="$OPTARG"
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

BROWSER_STRING=""
if [ \! -z "$BROWSER" ]; then
    BROWSER_STRING="--browser $BROWSER"
fi


TSTAMP=$(date +%Y-%m-%d_%H%M%S)
TEST_DIR="$TEST_ROOT"/"$TSTAMP"

if [ \! -d "$TEST_DIR" ]; then
    mkdir -p "$TEST_DIR"
fi
echo "Command: $0 $*" > "$TEST_DIR"/command.txt

if [ "$KILL_TESTS" == "true" ]; then
    kill_tests
    rm -fr client/cypress/screenshots/*
    rm -fr client/cypress/videos/*
fi

if [ "$RANDOM_GAME" == "true" ]; then
    if [ -z "$SEED" ]; then
	SEED=$(random_hex)
    fi
    echo "seed: $SEED" | tee "$TEST_DIR"/seed.txt
fi

if [ "$START_SERVER" == "true" ]; then
    start_server;
fi

sleep 1
cd client
TEST_DIR="../$TEST_ROOT"/"$TSTAMP"
RESULTS_DIR="$TEST_DIR/results"

if [ ! -d "$RESULTS_DIR" ]; then
    mkdir "$RESULTS_DIR"
fi

rm -f "RESULTS_DIR"/*

if [ "$RANDOM_GAME" == "true" ]; then
    MAX_RANDOM_INT_IN_BASH=32767 # min value is 0
    RANDOM=$((16#"$SEED" % $MAX_RANDOM_INT_IN_BASH)) # seed the generator to get predictable value

    if [ -z "$NUM_PLAYERS" ]; then
	NUM_PLAYERS=$((2 + $RANDOM % 9));
	echo "Num players: $NUM_PLAYERS"
    fi

    for player_index in $(seq 0 $(( $NUM_PLAYERS - 1 )) ); do
	if [ $player_index -gt 0 ] && [ \! -z "$STAGGERED_DELAY_IN_SEC" ]; then
	    sleep "$STAGGERED_DELAY_IN_SEC"
	fi
	start_player "rand" $player_index $FAST_MODE $NUM_PLAYERS $SEED
    done
else
    NUM_PLAYERS=4
    for player_index in $(seq 0 $(( $NUM_PLAYERS - 1 )) ); do
	if [ $player_index -gt 0 ] && [ \! -z "$STAGGERED_DELAY_IN_SEC" ]; then
	    sleep "$STAGGERED_DELAY_IN_SEC"
	fi
	start_player "det" $player_index $FAST_MODE $INC_RESTORED
    done
fi

sleep 5
cd "$START_DIR"
TEST_DIR="$TEST_ROOT"/"$TSTAMP"
RESULTS_DIR="$TEST_DIR/results"

exec_command_in_new_window Dashboard bash dashboard.sh "$RESULTS_DIR" &

while sleep 1; do
    NUM_PASSES=$(grep -l 'All specs passed' "$RESULTS_DIR"/player* | wc -l)
    if [ $NUM_PASSES -eq $NUM_PLAYERS ]; then
	echo "OK"
	exit 0
    else
	NUM_FAILS=$(grep -l fail "$RESULTS_DIR"/* | wc -l);
	if [ $NUM_FAILS -gt 0 ]; then
	    echo "FAIL"
	    exit $NUM_FAILS
	fi
    fi
done
    
