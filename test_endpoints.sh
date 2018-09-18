#!/bin/bash

TIMES=$1

echo "Query endpoints '$TIMES' times"

if [ "$TIMES" == "" ]; then
    echo "Please provide number of times to repeat the request"
    exit 1
fi

date +%s.%N
for (( i=0 ; $i < $TIMES ; i++ )); do
    #echo $i
    #curl -s http://localhost:3000/api/programs/current > /dev/null
    #curl -s http://localhost:3000/api/console/sensors > /dev/null
    #curl -s http://localhost:3000/api/batches > /dev/null
    #curl -s http://localhost:3000/api/status > /dev/null 
    curl -s http://localhost:3000/api/maintenance/info > /dev/null
done
date +%s.%N
