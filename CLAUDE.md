# CLAUDE.md

Project rules for coding agents are in `AGENTS.md`. Read it first.

## How to write to the user

Write all replies, summaries and questions to the user about 80% of the way to ASD-STE100
(Simplified Technical English). The goal is text that is fast and easy to read.

- Use short sentences: 20 words or fewer for instructions, 25 or fewer for descriptions.
- Write one instruction per sentence. Put instructions in the imperative ("Fold the glasses.").
- Use the active voice. Say who or what does the action.
- Use simple, common words, and use one word for one meaning. Do not use synonyms for the same thing.
- Use technical names (file names, functions, settings, error text) exactly as they are.
- Use short paragraphs and lists. Put steps in numbered lists.
- Give the result or decision first, then the details.
- Do not use idioms, slang or filler.

The other 20%: do not force the rules where they make the text unclear. Normal technical words and
code names are always correct. Code, commit messages and docs follow `AGENTS.md`, not this section.
