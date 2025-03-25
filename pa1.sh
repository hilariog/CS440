#!/bin/zsh

# Configuration
RUNS=300

# Start timing
START_TIME=$(date +%s)

# Initialize win counter
WIN_COUNT=0

# Run the program multiple times
for ((i = 1; i <= RUNS; i++)); do
    OUTPUT=$(java -cp "./lib/*:." edu.cwru.sepia.Main2 data/pas/stealth/OneUnitSmallMaze.xml)
    echo "Run #$i: $OUTPUT"
    
    # Check for a win
    if [[ $OUTPUT == *"The enemy was destroyed, you win!"* ]]; then
        ((WIN_COUNT++))
    fi
done

# End timing
END_TIME=$(date +%s)

# Calculate duration and percentage
DURATION=$((END_TIME - START_TIME))

# Print results
echo "Total Wins: $WIN_COUNT out of $RUNS runs"
echo "Total Time Taken: $DURATION seconds"