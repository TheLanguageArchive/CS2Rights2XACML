#!/bin/bash
# This shell script is released into the Public Domain; if it does you any good,
# feel free to use and/or modify it

# This script does a bunch of sanity checking and then launches the target JAR
# file for a Java application

# If the environment is not sane, it gives some useful feedback on the problem,
# using Zenity to display a dialog box, if available and not run from a terminal

# The main purpose of this is to notify a user when Java isn't installed or
# likely isn't installed correctly; there's also a check for an X server, for
# GUI applications to fail early on with a decent error message, instead of
# throwing an ugly HeadlessException


# Change directory to the location of this script, to avoid issues with relative
# paths to files referenced here
cd "${0%/*}"
	
# Some constants and variables for later
# Version Integer
#  14 = Java 1.4, 15 = Java 5, 16 = Java 6, 17 = Java 7, 18 = Java 8
TARGET_VER=18
# The JAR file to launch, relative to the directory of this script
# make this "$0" to execute a binary payload (on this script) as a JAR
TARGET_JAR="csrights2xacml-${project.version}-jar-with-dependencies.jar"

# Name of the application to display in error dialog
TARGET_NAME="csrights2xacml"
# This will be the start of the command we execute to start the Java process
# It may become more specific, if there's a valid JAVA_HOME, but it's not on the
# path
JAVA_LAUNCHER="java"
# Any required options for the JVM itself go here
JAVA_OPTIONS=""
# Any options you want passed to the application, before command-line parameters
# are passed
APPLICATION_OPTIONS=""

# Some strings to display for various error conditions
TOO_OLD="Java is installed, but is too old."
NO_JAVA_PATH="The Java executable has not been found on directories indicated by the 'PATH' variable.\nThis may indicate that Java isn't installed, or it may indicate that your 'PATH' variable does not include it."
PLEASE_INSTALL="Please install Java 8 or higher."
NO_X="No 'DISPLAY' variable set.\nThis application uses a GUI and cannot run without an X server."
DOWNLOAD_URL="http://java.com/en/download/"
DOWNLOAD_MESSAGE="You can download Java here: $DOWNLOAD_URL"
ERROR_TITLE="Unable to Start '${TARGET_NAME}'"

# Any of the checks below may switch this to false, forcing the script to
# terminate with an error message
RUN_TARGET=true

# Check if we're running from a terminal of some kind
ON_TERMINAL=true
if ! [ -t 0 ] ; then
	# We need this information later, for displaying dialog boxes instead of
	# unhelpfully displaying messages to Standard Out
	ON_TERMINAL=false
fi
# Check for Zenity; don't bother with dialog boxes without it
if ! which zenity >/dev/null ; then
	ON_TERMINAL=true
fi

# Check for a proper DISPLAY variable, to avoid using zenity if no X
ON_X=true
if ! [ -n "$DISPLAY" ]; then
	ON_TERMINAL=true
	ON_X=false
fi

# Check for Java's command-line lanucher on the path
JAVA_AVAILABLE=true
if ! which "$JAVA_LAUNCHER" >/dev/null ; then
	# Not on path, so try JAVA_HOME
	JAVA_LAUNCHER="$JAVA_HOME/bin/java"
	if ! [ -x "$JAVA_LAUNCHER" ] ; then
		echo -e "$NO_JAVA_PATH" >&2
		echo -e "$PLEASE_INSTALL" >&2
		echo -e "$DOWNLOAD_MESSAGE" >&2
		JAVA_AVAILABLE=false
		RUN_TARGET=false
	fi
fi

# Check for our target Java version
JAVA_OLD=false
if [ "$JAVA_AVAILABLE" = true ]; then
	JAVA_VER=$("$JAVA_LAUNCHER" -version 2>&1 | sed 's/.*version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
	if [ "$JAVA_VER" -lt "$TARGET_VER" ]; then
		echo -e "$TOO_OLD" >&2
		echo -e "$PLEASE_INSTALL" >&2
		echo -e "$DOWNLOAD_MESSAGE" >&2
		JAVA_OLD=true
		RUN_TARGET=false
	fi
fi

# This final check is for GUI applications that need an X server
# Comment out if you don't need this
# Check for a running X server
if ! [ "$ON_X" = true ]; then
	echo -e "$NO_X" >&2
	RUN_TARGET=false
fi

# Finally, all sanity checks are done: Start the program, using passed
#  command-line arguments!
if [ "$RUN_TARGET" = true ]; then
	"$JAVA_LAUNCHER" $JAVA_OPTIONS -jar "$TARGET_JAR" $APPLICATION_OPTIONS "$@"
# Or time to call Zenity to display the error dialog and exit with error code
elif ! [ "$ON_TERMINAL" = true ]; then
	# Coinstruct the final error message piece by piece
	ERROR_TEXT="The following error(s) have occured:\n"
	if ! [ "$JAVA_AVAILABLE" = true ]; then
		ERROR_TEXT="$ERROR_TEXT\n$NO_JAVA_PATH"
	fi
	if [ "$JAVA_OLD" = true ]; then
		ERROR_TEXT="$ERROR_TEXT\n$TOO_OLD"
	fi
	if [ "$JAVA_OLD" = true ] || ! [ "$JAVA_AVAILABLE" = true ]; then
		ERROR_TEXT="$ERROR_TEXT\n$PLEASE_INSTALL\n$DOWNLOAD_MESSAGE"
	fi
	zenity --error --title="$ERROR_TITLE" --text="$ERROR_TEXT"
	exit 1
# Or we're really out of options and have done our best: exit with error code
else
	exit 1
fi
# Prevent a binary payload from being executed as part of this script
# Also, a couple blank lines to make sure the payload doesn't interfere with the
# exit command; I made that mistake the first time I tested a payload
exit

# And this will ensure editors don't snip off the trailing blank lines