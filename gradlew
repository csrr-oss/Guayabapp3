#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Gradle Wrapper script — generado para Guayabapp
#

##############################################################################
# Helper Functions
##############################################################################
die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

log () {
    if ! "$app_quiet"; then
        echo "$*"
    fi
}

##############################################################################
# Detect OS
##############################################################################
app_quiet=false
for arg in "$@"; do
    case $arg in
        --quiet) app_quiet=true ;;
    esac
done

case $( uname ) in
    Darwin* ) os_type=Darwin  ;;
    CYGWIN* ) os_type=Cygwin  ;;
    MINGW*  ) os_type=MinGW   ;;
    MSYS*   ) os_type=Msys    ;;
    Linux*  ) os_type=Linux   ;;
    *       ) os_type=Other   ;;
esac

if [ "$os_type" = "Cygwin" ] || [ "$os_type" = "MinGW" ] || [ "$os_type" = "Msys" ]; then
    separator=";"
else
    separator=":"
fi

##############################################################################
# Locate Java
##############################################################################
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME
Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=$( which java ) || die "ERROR: JAVA_HOME is not set and no 'java' command could be found.
Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

##############################################################################
# Locate Gradle distribution
##############################################################################
APP_HOME=$( cd "${0%/*}" && pwd -P ) || die "Failure getting canonical path"

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

##############################################################################
# Execute Gradle
##############################################################################
exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
