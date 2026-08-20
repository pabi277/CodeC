# BusyBox CI fix

In `.github/workflows/bootstrap-userland.yml` replace:

```
make olddefconfig
```

with:

```
yes "" | make oldconfig
```

BusyBox 1.36.1 has no `olddefconfig` target (Makefile:444).
