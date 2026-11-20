# Roda as suítes Robot Framework e grava os relatórios em test/robot/results/.
#   .\run_tests.ps1              API e interface
#   .\run_tests.ps1 api          só API
#   .\run_tests.ps1 ui           só interface (exige `rfbrowser init chromium` uma vez)
#   .\run_tests.ps1 api --dryrun só checagem de sintaxe e imports
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$mode = if ($args.Count -gt 0) { $args[0] } else { 'all' }
$rest = if ($args.Count -gt 1) { $args[1..($args.Count - 1)] } else { @() }
switch ($mode) {
    'api' { $suites = @("$here/api/test") }
    'ui' { $suites = @("$here/ui/test") }
    'all' { $suites = @("$here/api/test", "$here/ui/test") }
    default { $suites = @("$here/api/test", "$here/ui/test"); $rest = $args }
}
robot --outputdir "$here/results" --name "AQuA-QE" @rest @suites
