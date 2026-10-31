param([string]$ModJar = "build/reobfJar/output.jar")
$ErrorActionPreference = "Stop"
$cache = Join-Path $env:USERPROFILE ".gradle/caches/modules-2/files-2.1"
$dependencies = @("org.spongepowered/mixin/0.8.5", "org.ow2.asm/asm/9.5", "org.ow2.asm/asm-tree/9.5",
    "org.ow2.asm/asm-commons/9.5", "org.ow2.asm/asm-util/9.5", "org.ow2.asm/asm-analysis/9.5",
    "com.google.guava/guava", "com.google.code.gson/gson", "com.mojang/logging", "org.slf4j/slf4j-api")
$jars = foreach ($dependency in $dependencies) {
    $found = Get-ChildItem (Join-Path $cache $dependency) -Recurse -Filter *.jar |
        Where-Object { $_.Name -notmatch 'processor|sources|javadoc' } | Select-Object -First 1
    if (-not $found) { throw "Missing cached test dependency: $dependency" }
    $found.FullName
}
$classes = "build/vanilla-music-policy-smoke/classes"
New-Item -ItemType Directory -Force $classes | Out-Null
$classpath = $jars -join ";"
& javac -proc:none -cp $classpath -d $classes tools/mixin-smoke/Smoke.java tools/mixin-smoke/Sandbox.java tools/mixin-smoke/WonderlandSmoke.java tools/mixin-smoke/WonderlandSandbox.java tools/mixin-smoke/MusicPolicySmoke.java tools/mixin-smoke/MusicPolicySandbox.java
if ($LASTEXITCODE -ne 0) { throw "Music policy test compilation failed" }
Copy-Item tools/mixin-smoke/META-INF -Destination $classes -Recurse -Force
Copy-Item tools/mixin-smoke/music-policy-smoke.json -Destination $classes -Force
foreach ($version in @("47.2.0", "47.4.10")) {
    $minecraft = Join-Path $env:USERPROFILE ".gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-$version/forge-1.20.1-$version-srg.jar"
    foreach ($side in @("CLIENT", "SERVER")) {
        & java -Xverify:all "-Dmixin.debug.countInjections=true" -cp "$classes;$classpath;$ModJar;$minecraft" MusicPolicySmoke "build/vanilla-music-policy-smoke/$version-$side" $ModJar $minecraft $side
        if ($LASTEXITCODE -ne 0) { throw "Music policy test failed ($version / $side)" }
    }
}
