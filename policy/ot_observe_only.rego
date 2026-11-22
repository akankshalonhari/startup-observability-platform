# Hard constraint 1a: any write path to an OT (L0) zone fails the build.
#
#   opa eval --fail-defined -d policy -i src/main/resources/policy/agent-tools.json 'data.observability.ot.deny[msg]'
#
# OtObserveOnlyPolicyTest enforces the same rule in the Maven build, and also checks that every agent
# tool in ReadOnlyTools is declared here as a read tool.
package observability.ot

import rego.v1

deny contains msg if {
	some tool in input.tools
	tool.mode != "read"
	"L0" in tool.zones
	msg := sprintf("tool %s has mode %s on an L0 OT zone; L0 is observe-only", [tool.name, tool.mode])
}
