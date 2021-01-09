#!/usr/bin/bash

RESULTS_DIR="$1"

watch "echo -e '*** ' $(dirname $RESULTS_DIR) ' ***\n';" \
      "cat $RESULTS_DIR/../command.txt;" \
      "cat $RESULTS_DIR/../seed.txt;" \
      "echo -e '\n\n*** SUCCESS ***\n';" \
      "grep -l 'All specs passed' $RESULTS_DIR/*;" \
      "grep -l 'All specs passed' $RESULTS_DIR/* | wc -l;" \
      "echo -e '\n\n*** FAILURE ***\n';" \
      "grep -l fail $RESULTS_DIR/*;" \
      "grep -l fail $RESULTS_DIR/* | wc -l;" \
      "echo -e '\n\n*** UNKOWN ***\n';" \
      "egrep -L '((All specs passed)|(fail))' $RESULTS_DIR/*;" \
      "egrep -L '((All specs passed)|(fail))' $RESULTS_DIR/* | wc -l;"
    
