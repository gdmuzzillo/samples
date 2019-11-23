# CoreTX

## Build commands

- `sbt publishLocal`: Locally publish a SNAPSHOT (just the .jar)
- `sbt publish-snapshot`: Locally publish a SNAPSHOT and it's Docker image
- `sbt release`: Increment the version to a release version, release the .jar, build and push the Docker image, push the Git tag, and increment to the next snapshot

### Extra Release commands

Additional commands to `release`:
- `with-defaults`: Do not prompt for next versions, just assume them
- `release-version x.y.z next-version x1.y1.z1`: Pre-set the release and next versions
- `skip-tests`: Self explanatory