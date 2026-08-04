# Model Routing Profiles
# Directory: agent-config/profiles/
#
# Each YAML file defines model routing rules for a specific pipeline.
# Cost-controller skill reads these at pipeline start.
#
# Tier definitions:
#   high:  Opus / GPT-4o         — complex reasoning, critical decisions
#   mid:   Sonnet / GPT-4o-mini  — code gen, structured writing
#   low:   Local models (free)   — formatting, templates, logging
