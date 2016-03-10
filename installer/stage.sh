#!/bin/sh

CURDIR=${PWD}
TEMPDIR=$CURDIR/tmp

#REPO Definitions
SAVE_REPO=("https://scm.sri.com/scm/git/SAVE/SAVE" "master" "save")
VWF_REPO=("https://github.com/AddictArts/vwf.git" "SAVE/M4" "vwf")
XSB_REPO=("svn://svn.code.sf.net/p/xsb/src/trunk" "" "xsb")
FLORA_REPO=("svn://svn.code.sf.net/p/flora/src/trunk/flora2" "" "flora")
TASKLEARNING_REPO=("https://cmxt.sri.com/svn/tasklearning/branches/training" "" "tasklearning")

SVN_ARTIFACTS=(".svn,d")
GIT_ARTIFACTS=(".git,d" ".gitignore,f")

ARTIFACT_LIST=("${SVN_ARTIFACTS[@]}" "${GIT_ARTIFACTS[@]}")


 #NODEJS=$(wget http://nodejs.org/dist/latest/node-v0.10.35-x86.msi)
 
 
 function stage_save () {
	git clone -b ${SAVE_REPO[1]} --depth 1 ${SAVE_REPO[0]} $TEMPDIR/${SAVE_REPO[2]}
 }
 
 function stage_vwf () {
	git clone -b ${VWF_REPO[1]} --depth 1 ${VWF_REPO[0]} $TEMPDIR/${VWF_REPO[2]}
 }
 
 function stage_xsb () {
	svn co ${XSB_REPO[0]} $TEMPDIR/${XSB_REPO[2]}
 }
 
 function stage_flora () {
	svn co ${FLORA_REPO[0]} $TEMPDIR/${FLORA_REPO[2]}
 }
 
 function stage_tasklearning () {
	svn co --username bhoskins ${TASKLEARNING_REPO[0]} $TEMPDIR/${TASKLEARNING_REPO[2]}
 }
 
 function remove_artifacts () {
	for artifact_pair in "${ARTIFACT_LIST[@]}"
	do
		for artifact in $artifact_pair
		do
			local _array=($(echo $artifact | tr "," "\n"))
			find $TEMPDIR -name ${_array[0]} -type ${_array[1]}
		done
	done
 }
 
function pre_stage () {
	rm -rf $TEMPDIR
	mkdir -p $TEMPDIR
 }
 
function stage () {
	#stage_save
	#stage_vwf
	#stage_xsb
	#stage_flora
	stage_tasklearning
}

function post_stage () {
	remove_artifacts
}

#pre_stage
stage
post_stage

