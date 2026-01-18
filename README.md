# Hallowed Sepulchre Tracker

A RuneLite plugin that tracks your progress in the Hallowed Sepulchre minigame.

## Features

### Real-Time Tracking
- **Live XP/hr** - Updates constantly during runs
- **Run timer** - Track your current run time
- **Floor timer** - Time spent on each floor

### Statistics
- **Today's Stats** - Runs, XP gained, time spent, XP/hr
- **All-Time Stats** - Total runs, XP, hours played across all sessions
- **Floor Completions** - Parsed directly from game chat for accuracy
- **Personal Best Times** - Captured from game messages

### Progress Tracking
- XP and runs remaining to level 92 and 99
- Estimated days to reach goals (based on your average runs per day)
- Uses your actual average XP per run for accurate projections

### Smart Display
- Info box only appears when inside Hallowed Sepulchre
- Collapsible daily history section
- Clean, readable UI with color-coded statistics

## Configuration

- **Show Overlay** - Toggle the in-game run overlay
- **Show Info Box** - Toggle the stats info box
- **Estimated XP per Run** - Fallback estimate for new users
- **Target Level** - Your agility goal (default: 99)

## Installation

1. Open RuneLite
2. Go to the Plugin Hub
3. Search for "Hallowed Sepulchre Tracker"
4. Click Install

## Building from Source

```bash
./gradlew build
```

## Development

This plugin was built with **vibe coding** 🤙

## License

BSD 2-Clause License
