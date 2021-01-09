#!/usr/bin/bash

# Global variables in CAPS, local variables in lower case.
START_DIR=$(pwd)
TEST_ROOT="./test_results"


print_usage() {
    printf "USAGE: $0 [-fhjrswx] [-u URL] [-d SEED]\n"
    printf "\n"
    printf "\t-h:\t\tPrint this message and exit\n"
    printf "\t-f:\t\tFast mode (default off)\n"
    printf "\t-j:\t\tRun from jar (default off)\n"
    printf "\t-k:\t\tKill old tests and server before starting (default off)\n"
    printf "\t-q:\t\tPlay a random game (default off)\n"
    printf "\t-r:\t\tInclude tests of restored games (default off)\n"
    printf "\t-s:\t\tServer already running, don't start a new one (default off, so new server instance will be started)\n"
    printf "\t-w:\t\tOpen browser windows (default off)\n"
    printf "\t-x:\t\tExit browser at end of test (default off)\n"
    printf "\t-d SEED:\tSpecify seed for random game\n"
    printf "\t-p PORT:\t\tSpecify lowest port to use (default 10000)\n"
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
	# Don't know how to start cygwin maximised
	shift 1 # ignore title, don't know how to set it
	cygstart C:/cygwin64/bin/mintty.exe -i /Cygwin.ico "$*"
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
    
    exec_command_in_new_max_window Server "$server_command" --create-files false --delete-existing false --logging sysout test_games/1000/11 test_games/1001/14 | tee "$TEST_DIR"/server-logs.txt &
}

append() {
    if [ -z "$1" ]; then
	echo -n "$2";
    else
	printf "$1","$2"
    fi
}

build_cypress_env_common() {
    player_index="$1"
    fast_mode="$2"
    url="$3"

    TEMP_FILE_DIR=$(fix_cygwin_path "$TEST_DIR")/temp_files
    ENV="TEMP_DIR=$TEMP_FILE_DIR,PLAYER_INDEX=$player_index,FAST_MODE=$fast_mode,URL=$url"

    echo -n "$ENV"
}

build_cypress_env_random() {
    player_index="$1"
    fast_mode="$2"
    url="$3"
    num_players="$4"
    seed="$5"
    
    ENV=$(build_cypress_env_common "$player_index" "$fast_mode" "$url")
    ENV=$(append "$ENV" "RANDOM=true,NUM_PLAYERS=$num_players,SEED=$seed")

    echo -n "$ENV"
}

build_cypress_env_deterministic() {
    player_index="$1"
    fast_mode="$2"
    url="$3"
    inc_restored="$4"

    ENV=$(build_cypress_env_common "$player_index" "$fast_mode" "$url")
    ENV=$(append "$ENV" "INC_RESTORED=$inc_restored")

    echo -n "$ENV"
}

start_player() {
    mode="$1"
    if [ "$mode" == "det" ]; then
	player_index="$2"
	fast_mode="$3"
	url="$4"
	inc_restored="$5"

	ENV=$(build_cypress_env_deterministic "$player_index" "$fast_mode" "$url" "$inc_restored")
	port=$(($PORT_BASE + "$player_index"))
	result_file="$RESULTS_DIR/player${player_index}-report"
	exec_command_in_new_window "Player $(($player_index + 1))" npx cypress run -s cypress/integration/celebrity-tests.js --env "$ENV" $HEAD $EXIT -p $port '>' "$result_file"  &
    elif [ "$mode" == "rand" ]; then
	player_index="$2"
	fast_mode="$3"
	url="$4"
	num_players="$5"
	seed="$6"

	ENV=$(build_cypress_env_random "$player_index" "$fast_mode" "$url" "$num_players" "$seed")
	port=$(($PORT_BASE + "$player_index"))
	result_file="$RESULTS_DIR/player${player_index}-report"
	exec_command_in_new_window "Player $(($player_index + 1))" npx cypress run -s cypress/integration/celebrity-tests.js --env "$ENV" $HEAD $EXIT -p $port '>' "$result_file"  &
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
URL="default"
while getopts "hfjkqrswxu:d:p:" OPT; do
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
	d)
	    SEED="$OPTARG"
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

TEST_TYPE="full"
if [ "$FAST_MODE" == "true" ]; then
    TEST_TYPE="fast"
fi

RESULTS_DIR_PREFIX="$TEST_DIR/results"

if [ ! -d "$RESULTS_DIR_PREFIX"-fast ]; then
    mkdir "$RESULTS_DIR_PREFIX"-fast
fi
if [ ! -d "$RESULTS_DIR_PREFIX"-full ]; then
    mkdir "$RESULTS_DIR_PREFIX"-full
fi
RESULTS_DIR="$RESULTS_DIR_PREFIX"-"$TEST_TYPE"

rm -f "RESULTS_DIR"/*

if [ "$RANDOM_GAME" == "true" ]; then
    MAX_RANDOM_INT_IN_BASH=32767 # min value is 0
    RANDOM=$((16#"$SEED" % $MAX_RANDOM_INT_IN_BASH)) # seed the generator to get predictable value
    
    NUM_PLAYERS=$((2 + $RANDOM % 9));

    for player_index in $(seq 0 $(( $NUM_PLAYERS - 1 )) ); do
	start_player "rand" $player_index $FAST_MODE $URL $NUM_PLAYERS $SEED
    done
else
    NUM_PLAYERS=4
    for player_index in $(seq 0 $(( $NUM_PLAYERS - 1 )) ); do
	start_player "det" $player_index $FAST_MODE $URL $INC_RESTORED
    done
fi

sleep 5
cd "$START_DIR"
TEST_DIR="$TEST_ROOT"/"$TSTAMP"
RESULTS_DIR_PREFIX="$TEST_DIR/results"
RESULTS_DIR="$RESULTS_DIR_PREFIX"-"$TEST_TYPE"

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
    
