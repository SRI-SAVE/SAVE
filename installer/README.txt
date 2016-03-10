SAVE INSTALLER README

The current installer is an commercial product called Advanced Installer. It can be
located here -> http://www.advancedinstaller.com/download.html

NOTE: You do not need a clone of SAVE-srv or VWF git repositories following these steps.

CURRENT MANUAL STEPS

	0.	Download installtree.zip from
		http://savebuild.cse.sri.com:8080/job/SAVE_installdata/

	1.	Unzip (Extract All) the installtree.zip file. This will create an
		installtree/ folder

	2.	Move SAVE-src/ to the same folder as installtree/
		Ex:
			installtree/
				...
			SAVE-src/
				...

	3.	Copy the installers foler in SAVE-src/installer/3rd-party-installers to
		installtree/ then delete the SAVE-src/installer/3rd-party-installers/
		directory from SAVE-src/installer/
		Ex:
			installtree/
				installers/

	3.	Copy existing install of node_modules or perform 3 (alternative) to
		installtree/VWF/node_modules
		Ex:
			installtree/
				VWF/
					node_modules/
		NOTE: You may need to create
		C:\Users\[your username]\Appdata\roaming\npm directory and npm must be
		run as Administrator.

	4.	Open installer build file SAVE.aip in SAVE-src/installer/ and edit the
		version etc. ...
		Product Details
			Product Version
		Media
			Default Build
				MSI name

		NOTE: Recommend keeping the existing product code for the major version

	5.	Copy all files in holding directory SAVE-src/installer/holding/ (except
		the license file) to installtree/bin/

	6.	You may need to update the installer project Resources/Files and
		Folders with new files and removed files. The removed files reference
		should be deleted. Deleting the reference to the root folder use drag
		and drop to add the top level folder back into the project the easiest.

	7.	Build the installer using the "Run" menu bar action or selecting the
		"Product Details" and the "Run" toolbar button.

CURRENT KNOWN ISSUES

