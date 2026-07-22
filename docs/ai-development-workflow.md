# How I Built Termux Launcher with AI Coding Tools

## The early workflow

I did not start with an advanced harness or multi-agent orchestration setup. There was nothing particularly fancy: I used either the Codex CLI or Claude Code CLI, all on standard $20 subscription plans.

I started the project with GPT-5.2. At that point, it was difficult to get the model to do exactly what I wanted. I would ask it to change one thing, and it would often break three others. I then had to repair those regressions one by one, which could take days.

The project began making real progress when Codex 5.3 was released. The model would finally do what I asked without constantly breaking unrelated functionality. However, the prompts still had to be extremely detailed—often several paragraphs long. Some examples are available in [example prompts](./example-prompts.md).

As GPT-5.4 and GPT-5.5 arrived, the process became progressively easier. The prompts became much shorter, and the models required less explanation to produce useful results.

Throughout this period, I worked on one feature at a time: implementing it, testing it, refining it, and then building on top of it.

## Adding Claude to the workflow

I later gained access to a Claude subscription through my company, which made the process even easier. Claude was especially useful for design-related work.

Today, I use a multi-agent orchestration workflow. It is a little more sophisticated, but still not especially flashy.

### Delegating work across coding agents

I created a Claude Code skill that can delegate tasks to Codex CLI and OpenCode CLI in headless mode. The skill assigns suitable tasks to those models when necessary, while Claude acts as the orchestrator.

Opus needs frequent reminders to delegate work, but Fable was exceptionally consistent at using the skill effectively.

### Automated Android testing through ADB

I also created a skill for both Codex CLI and Claude Code that allows them to control an Android phone through ADB.

I keep an old HTC 10 running Android 13 through LineageOS connected to my PC. After each feature is added, Claude or Codex can:

1. Connect to the phone.
2. Run the test suite.
3. Identify what is broken.
4. Fix the problems.
5. Retest until the result reaches an acceptable state.
6. Install the updated app on my primary phone for manual testing.

I then provide feedback when necessary, and the cycle repeats.

## A major example: the standalone package name

Some recent major features were implemented almost entirely by Fable. The standalone package-name work is a good example.

Fable effectively operated my development machine autonomously for four days. It began by creating a Docker-based package build environment while, in parallel, creating a separate branch for the new package name.

Once that foundation was ready, I provided a list of packages installed on my daily-driver device as a starting point. It first built the bootstrap packages, then the packages I already had installed, followed by additional packages. It deliberately skipped some packages that would have taken too long to compile on my PC.

For planning, I used ChatGPT on the web, which suggested using Cloudflare R2 to host the package repository.

After I provided the required Cloudflare credentials, Fable would:

1. Build packages.
2. Publish them in batches.
3. Install them on the Android test device.
4. Verify that they worked.
5. Repeat the process.

When triggering a binary build, Fable would estimate how long the build might take, create a follow-up reminder for itself, check the status later, and continue from there.

## Another example: the in-app keyboard

Adding the in-app keyboard was similarly easy from my side.

I explained that Android's IME closes when I leave the app and has to be opened again when I return to the home screen. To remove that friction, I wanted an in-app keyboard similar to Unexpected Keyboard.

Fable then delegated work to Codex and OpenCode to:

- Map the relevant repositories.
- Identify the correct integration points.
- Review licensing requirements.
- Plan the implementation.

It asked me for confirmation and then carried out the work.

I only needed one steering prompt near the end. The first version of the keyboard was too short, so I asked it to add height controls.

The rest of the workflow followed the same pattern as before: it performed real-world testing on the connected phone, identified problems, revised the implementation, and repeated the process until the feature was production-ready.

## Looking back

The difference between where I started and what is possible now is enormous. The workflow has become dramatically easier and more reliable.

With a larger budget, I could likely expand the automation further and run more agents or build additional specialized workflows. Even with the current setup, however, the improvement has been substantial.