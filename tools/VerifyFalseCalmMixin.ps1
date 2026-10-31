param(
    [string]$ModJar = "build/reobfJar/output.jar",
    [string]$RusJar = "libs/rus-patch-1.3_6-1.9.3.jar",
    [string]$Output = "build/mixin-smoke/transformed"
)
$ErrorActionPreference = "Stop"
$cache = Join-Path $env:USERPROFILE ".gradle/caches/modules-2/files-2.1"
$dependencies = @(
    "org.spongepowered/mixin/0.8.5",
    "org.ow2.asm/asm/9.5",
    "org.ow2.asm/asm-tree/9.5",
    "org.ow2.asm/asm-commons/9.5",
    "org.ow2.asm/asm-util/9.5",
    "org.ow2.asm/asm-analysis/9.5",
    "com.google.guava/guava",
    "com.google.code.gson/gson"
    "com.mojang/logging"
    "org.slf4j/slf4j-api"
)
$jars = foreach ($dependency in $dependencies) {
    $found = Get-ChildItem (Join-Path $cache $dependency) -Recurse -Filter *.jar |
        Where-Object { $_.Name -notmatch 'processor|sources|javadoc' } | Select-Object -First 1
    if (-not $found) { throw "Missing cached test dependency: $dependency" }
    $found.FullName
}
$classes = "build/mixin-smoke/classes"
New-Item -ItemType Directory -Force $classes | Out-Null
$classpath = $jars -join ";"
& javac -proc:none -cp $classpath -d $classes tools/mixin-smoke/Smoke.java tools/mixin-smoke/Sandbox.java
if ($LASTEXITCODE -ne 0) { throw "Smoke test compilation failed" }
Copy-Item tools/mixin-smoke/META-INF -Destination $classes -Recurse -Force
Copy-Item tools/mixin-smoke/falsecalm-smoke.json -Destination $classes -Force
& java -Xverify:all "-Dmixin.debug.countInjections=true" -cp "$classes;$classpath;$ModJar;$RusJar" Smoke "$Output/server" $RusJar SERVER
if ($LASTEXITCODE -ne 0) { throw "Actual Mixin transformation failed" }
& java -Xverify:all "-Dmixin.debug.countInjections=true" -cp "$classes;$classpath;$ModJar;$RusJar" Smoke "$Output/client" $RusJar CLIENT
if ($LASTEXITCODE -ne 0) { throw "Client-side Mixin transformation failed" }
