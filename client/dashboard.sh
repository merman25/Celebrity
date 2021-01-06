#!/usr/bin/bash

watch 'echo -e "*** SUCCESS ***\n";' \
      'grep -l "All specs passed" results*/*;' \
      'grep -l "All specs passed" results*/* | wc -l;' \
      'echo -e "\n\n*** FAILURE ***\n";' \
      'grep -l fail results*/*;' \
      'grep -l fail results*/* | wc -l;' \
      'echo -e "\n\n*** UNKOWN ***\n";' \
      'egrep -L "((All specs passed)|(fail))" results*/*;' \
      'egrep -L "((All specs passed)|(fail))" results*/* | wc -l;'
    
