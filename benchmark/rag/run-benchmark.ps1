param(
    [Parameter(Mandatory = $true)]
    [string]$Maven,
    [string]$Scales = "1000,10000,50000",
    [int]$Measurements = 0,
    [int]$Warmup = 100
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$backend = Join-Path $repo "backend"
$results = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $results | Out-Null

$metadata = @(
    "timestamp=$(Get-Date -Format o)"
    "gitCommit=$(git -c safe.directory=E:/piagent -C $repo rev-parse HEAD)"
    "scales=$Scales"
    "measurements=$Measurements"
    "warmup=$Warmup"
    "computer=$env:COMPUTERNAME"
    "processor=$env:PROCESSOR_IDENTIFIER"
    "logicalProcessors=$env:NUMBER_OF_PROCESSORS"
    "os=$([System.Environment]::OSVersion.VersionString)"
    "java=$(& java -version 2>&1 | Select-Object -First 1)"
)
Set-Content -LiteralPath (Join-Path $results "environment.txt") -Value $metadata -Encoding UTF8

Push-Location $backend
try {
    & $Maven '-Dtest=BatchEmbeddingBenchmarkTest' test 2>&1 |
        Tee-Object -FilePath (Join-Path $results "maven-batch-embedding.log")
    if ($LASTEXITCODE -ne 0) { throw "Batch Embedding test failed: $LASTEXITCODE" }

    & $Maven '-Dtest=RagIndexReliabilityIT' '-Drag.it.enabled=true' test 2>&1 |
        Tee-Object -FilePath (Join-Path $results "maven-reliability.log")
    if ($LASTEXITCODE -ne 0) { throw "RAG reliability tests failed: $LASTEXITCODE" }

    & $Maven '-Dtest=RagIndexReliabilityIT#concurrentBuildsMustNotLeaveDocumentPointerDifferentFromVisibleVersion' `
        '-Drag.it.enabled=true' '-Drag.concurrent-diagnostic.enabled=true' test 2>&1 |
        Tee-Object -FilePath (Join-Path $results "maven-concurrent-build-diagnostic.log")
    if ($LASTEXITCODE -eq 0) {
        Set-Content -LiteralPath (Join-Path $results "concurrent-build-diagnostic.txt") `
            -Value "PASS: same-document concurrent rebuild invariant held in this run." -Encoding UTF8
    }
    else {
        Set-Content -LiteralPath (Join-Path $results "concurrent-build-diagnostic.txt") `
            -Value "FAIL: reproduced same-document concurrent rebuild invariant violation. See Maven log." -Encoding UTF8
    }

    $arguments = @(
        '-Dtest=RagRetrievalBenchmarkIT'
        '-Drag.benchmark.enabled=true'
        "-Drag.benchmark.scales=$Scales"
        "-Drag.benchmark.warmup=$Warmup"
        "-Drag.benchmark.output=$results"
        'test'
    )
    if ($Measurements -gt 0) { $arguments = $arguments[0..3] + "-Drag.benchmark.measurements=$Measurements" + $arguments[4..($arguments.Length - 1)] }
    & $Maven @arguments 2>&1 | Tee-Object -FilePath (Join-Path $results "maven-retrieval.log")
    if ($LASTEXITCODE -ne 0) { throw "RAG retrieval benchmark failed: $LASTEXITCODE" }
}
finally {
    Pop-Location
}
