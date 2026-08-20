# BusyBox configuration history

This file records an abandoned direct-NDK experiment; it is not the final build
method.

BusyBox 1.36.1 has no `olddefconfig` target. During the experiment, configuration
required:

```sh
set +o pipefail
yes "" | make oldconfig
set -o pipefail
```

`oldconfig` closes input when complete, causing `yes` to receive expected
SIGPIPE. Linux `defconfig` also enabled applets unavailable on Android, including
virtual-console tools and APIs missing from Bionic.

## Final decision

The production bootstrap uses the official Termux BusyBox recipe and maintained
Android configuration through `termux-packages/scripts/run-docker.sh`. Do not
restore the hand-written NDK BusyBox workflow or continue adding one-off applet
disables.
