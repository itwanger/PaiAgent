param(
    [string]$Maven = "E:\apache-maven-3.9.16\bin\mvn.cmd",
    [int]$VectorCount = 50000,
    [int]$Dimension = 1024,
    [int]$Queries = 100,
    [int]$Measurements = 100,
    [int]$Warmup = 50,
    [string]$IndexConfigs = "16:64,24:64,32:64,16:128,16:256",
    [string]$EfSearchValues = "40,80,120,200,400",
    [string]$RunName = "hnsw-control"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$backend = Join-Path $repo "backend"
$output = Join-Path $PSScriptRoot "results\$RunName"
New-Item -ItemType Directory -Force -Path $output | Out-Null

$metadata = @(
    "timestamp=$(Get-Date -Format o)"
    "gitCommit=$(git -c safe.directory=E:/piagent -C $repo rev-parse HEAD)"
    "vectorCount=$VectorCount"
    "dimension=$Dimension"
    "queries=$Queries"
    "measurements=$Measurements"
    "warmup=$Warmup"
    "indexConfigs=$IndexConfigs"
    "efSearchValues=$EfSearchValues"
    "computer=$env:COMPUTERNAME"
    "processor=$env:PROCESSOR_IDENTIFIER"
    "logicalProcessors=$env:NUMBER_OF_PROCESSORS"
    "os=$([System.Environment]::OSVersion.VersionString)"
    "java=$(& java -version 2>&1 | Select-Object -First 1)"
)
Set-Content -LiteralPath (Join-Path $output "host-environment.txt") -Value $metadata -Encoding UTF8

Push-Location $backend
try {
    $arguments = @(
        '-Dtest=HnswRecallControlExperimentIT'
        '-Drag.hnsw.control.enabled=true'
        "-Drag.hnsw.vector-count=$VectorCount"
        "-Drag.hnsw.dimension=$Dimension"
        "-Drag.hnsw.query-count=$Queries"
        '-Drag.hnsw.top-k=10'
        "-Drag.hnsw.measurements=$Measurements"
        "-Drag.hnsw.warmup=$Warmup"
        "-Drag.hnsw.index-configs=$IndexConfigs"
        "-Drag.hnsw.ef-search-values=$EfSearchValues"
        '-Drag.hnsw.create-filter-index=true'
        "-Drag.hnsw.output=$output"
        '-DargLine=-Xms512m -Xmx4g'
        'test'
    )
    & $Maven @arguments 2>&1 | Tee-Object -FilePath (Join-Path $output "maven.log")
    if ($LASTEXITCODE -ne 0) { throw "HNSW control experiment failed: $LASTEXITCODE" }
}
finally {
    Pop-Location
}
