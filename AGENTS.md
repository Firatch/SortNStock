# Standing instructions for this project

These apply to every change in this repository, without the user having to ask again.

## 1. Always install the build after every change

After any code change, build **and** install the jar into the test instance in one step:

```
$env:JAVA_HOME = 'C:\Program Files\Java\zulu-25'
$env:SORTNSTOCK_MODS_DIR = '<test-instance>\.minecraft\mods'
pwsh -File tools\build-and-install.ps1
```

That script builds, refuses to install if the build or any test failed, verifies the destination
path, copies the jar to

```
<test-instance>\.minecraft\mods\sortnstock-26.1.2-0.1.0.jar
```

and prints the jar hash, size and timestamp. **Never hand-roll the `gradlew` + `Copy-Item` pair** —
that is how a stale jar ends up in front of the user.

The script also reports whether Minecraft is currently running. A jar replaced while the game is
running is **not** loaded, and the next test would silently use the old build. If it warns that the
game is running, say so explicitly in the reply and ask for a full restart.

## 2. Say which build was installed

End the reply with the installed jar hash and time, so the user can confirm the game loaded exactly
that build.

## 3. Never claim in-game behaviour was verified

Unit tests cover logic only. Anything that needs the game — GUI rendering, mixin application, hotkey
delivery, click execution, compatibility with other mods — stays **待用户验证** until the user
reports a result. Do not describe `runClient` as a functional test; it is a launch smoke test.

## 4. Read the real logs before guessing

When the user reports a bug, read
`<test-instance>\.minecraft\logs\latest.log` (and the newest `*.log.gz`) before
forming a hypothesis. Check the **timestamps** against the jar's modification time first: more than
one round has been spent diagnosing a build that was never loaded.

## 5. Test first where the logic allows it

For a reported bug, add a failing test where the logic is Minecraft-free, then fix it. Where the
behaviour is Minecraft-bound, say plainly that it is untested rather than implying coverage.
