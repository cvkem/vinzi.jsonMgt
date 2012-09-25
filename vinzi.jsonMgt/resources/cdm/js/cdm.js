

/*
 *  define some globals that will be exposed to cde and will be connected to the interface there.
 */
var srcBox, srcFv, dstBox, dstFv;




function strObj(obj) {
  if ((typeof obj == 'string') ||
      (typeof obj == 'number') ||
      (typeof obj == 'boolean'))
    return ''+obj;
  var s = '';
  for (var x in obj) {
    if (obj.hasOwnProperty(x))
       s += x+':'+obj[x]+';\n';
  }
  return s;
};

function showError(prefix, err) {
	  var msg = prefix + '\n\n';
	  if (typeof err.message != 'undefined')
	     msg += err.message + '\n\n';
	  msg += strObj(err);

	  alert(msg);
	  return;
	}




var namesPrefixed = false;

function getCurrentUser() {
    var user = arguments[0];
    user = (user) ? user : Dashboards.context.user;

    if (namesPrefixed) {
        var prefix = user.slice(0,4);
        if ((prefix == prefix.toUpperCase()) && (prefix[3] == '-'))
            user = user.slice(4);
    }
    return user;
}


function cdpExec(pars, updateFunc) {
    if ((typeof theCdpFile !== 'string') || (theCdpFile.length == 0))
        console.log("expected to receive string 'cdpFile'. Received: "+theCdpFile);
    var base="/pentaho/content/cdp/exec?"+theCdpFile;
    
//    alert ('Going to call  CDP with parameters: '+ strObj(pars));
    
    $.post(base, pars, updateFunc);
    
    return;
};


function updateTable(tblSel, srcSel, targetDiv) {
	  var pars = {accessId: 'showTable',
			       tblSel: tblSel,
			   		source: srcSel}
	  cdpExec(pars, function(data, s) {
		  				if (data.length > 1)
		  					alert('can only handle first element. Discarding'+data.slice(1));
		  				var data = data[0];  // take first element
		  				$(targetDiv).html(data);
		      });
	};

	function updateLogTables(srcSel) {
	  /*
	    update log-table and error-table
	   */
	//  alert('running updates'+srcSel);
	  updateTable('actions', srcSel, '#actionDiv');
	  updateTable('errors', srcSel, '#errorDiv');
	};

	var partialCommand = null;  // used to store command that still needs a destination via the 'destPopup' window 

	// needs a destination value.
	function finish_partialCommand() {
		if (partialCommand == null) {
			alert(" STRANGE!! no command available");
			return;
		}
		partialCommand.dst = dstBox.getValue();

	    cdpExec(partialCommand, function (d, s) {
            if (d.length > 0)
            	alert(d);
            updateLogTables(srcBox.getValue());   //() added
        });
		partialCommand = null;
		return;
	};

 function dst_ok_button () { 
	    dstFv.accept();
	    finish_partialCommand();
	    destPopup.hide();
	    return;};

 function dst_cancel_button () { 
	    dstFv.reject();
	    partialCommand = null;  // operation cancelled
	    destPopup.hide();
	    return;
	};
	


/*
  Below are the interface functions that collect the parameters and
  call the cdP-engine for handling the actual data-processing.
  (implementation of the dashboard buttons)
*/

	
function run_action (action) {
	    var pars = {accessId: 'process-jsonMgt-command',
	                  action: action,
	                  user: getCurrentUser(),
	                  source: "",
	                  destinations: "",
	                  msg: ( arguments[1] ) ? arguments[1] : ""  };
	                                            
	    cdpExec(pars, function (d, s) {
	    	                            if (d.length > 0)
	    	                            	alert(d);
//	                                    updateLogTables(srcBox.getValue());  //() added
	                                });
	   return;
	};
	
	
function run_action_src (action) {
    var pars = {accessId: 'process-jsonMgt-command',
                  action: action,
                  user: getCurrentUser(),
                  source: srcBox.getValue(),
                  destinations: "",
                  msg: ( arguments[1] ) ? arguments[1] : ""   };
                                            
    cdpExec(pars, function (d, s) {
    	                            if (d.length > 0)
    	                            	alert(d);
                                    updateLogTables(srcBox.getValue());  //() added
                                });
   return;
};

function add_dest_run_action (action) {
	// prepare a partial command
  partialCommand = 	{accessId: 'process-jsonMgt-command',
          action: action,
          source: srcBox.getValue(),
          destinations: "",
          msg: ( arguments[1] ) ? arguments[1] : ""   };
  // and popup the window to get a destination (ok-button will proceed, cancel will clear the partialCommand.
  $('.destPopup').show();
  return;
}

function helpFunc () 
{
    var pars = {accessId: 'process-jsonMgt-command',
                  action: "help",
                  user: getCurrentUser(),
                  source: "",
                  destinations: "",
                  msg: ""  };
                                            
    cdpExec(pars, function (d, s) {
                                    Dashboards.fireChange('refreshTables', refreshTables+1);
                                    alert(d[0]);
                                });

   return;
}

$(function () {
	// Add a new exclusion (or update an existing exclusion)
	$("#commitDialog").dialog({autoOpen: false,
                    width: 450,
                    modal: true,
                    closeText: 'Annuleer',
                    open: function() {
                      try {
                    	  var dialogPrefix = '#commitDialog ';
                         // set information items at top of box.
                         $(dialogPrefix+'#filemask').html('<strong>files</strong>: '+srcBox.getValue());
                         
                         // set the dialog-defaults
                         $(dialogPrefix+'#msg').val('');

                         return;
                       } catch (err) {
                               showError('Error during "Open commit-dialog":', err);
                       }

                     },
                     buttons: {
                         "Commit": function() {
                           try {
                               var dialogPrefix = '#commitDialog ';
 
                               var msg = $(dialogPrefix+'#msg').val();

                               run_action_src('commit', msg);
                             
                               $(this).dialog('close');
                             return;
                           } catch (err) {
                               showError('Error during "Commit":', err);
                           }
                         }
                     }
 		});  // END of commitDialog
                        	
});  // END of dialog handlers
/*
 end interface functions  (implementations of the different buttons.)
*/




/* Clojure for asynchronous retrieval of file-root.
  This is a closure that requests the file-root and performs retries as long as the 
  server returns an empty string.
  The getFileTreeRoot does a check whether the value is available.
 */
/*
function createFileTreeRootContainer (initFileTreeUsers) {
    //  The root of the file-system
    var theFileTreeRoot = null;
    var ftrCont = {
	setFileTreeRoot: function () {
	    //
	    //  Function to retrieve the FileTreeRoot from the server.
	    //  The response should be plain format text.
	    //
	    theFileTReeRoot = null;
	    var iter = 1;
	    var getDocRoot = function() {
		$.post('/cdm/jqDocRoot', 
	    { }, 
		       function(data) {
			   if (data && data.length > 0) {
			       theFileTreeRoot = data;
			       initFileTreeUsers();
			   } else { 
			       // fire a new request if the response is ""
			       iter++;
			       getDocRoot();
			   }
		       })
	    };


	    //retry the set-procedure until a doc-root is passed.
	    // the response time of the server prevents this loop
	    //  from consuming to much processor-time
	    getDocRoot();
	},
	    getFileTreeRoot: function() {
	    // wait for theFileRoot te be set

	    while (!theFileTreeRoot) { 
		setTimeout(function() {return;}, 10)};

	    return theFileTreeRoot;}
    }; //end of return

    // trigger the request for the current root
    ftrCont.setFileTreeRoot();
    return ftrCont;
};  // end of closure

*/




function createSelectBox(targetDiv) {
    return {
      obj: $(targetDiv),
      txt: '',
      setValue: function(newTxt) {
	  this.txt = newTxt;
	  this.obj.text(this.txt);
	  return;},
      getValue: function() { return this.txt;}
    };
};



function createFileViewer(targetDiv, selBox, updateFunc) {
 //   var updateFunc1 = (typeof (updateFunc) == 'function')?
//	              updateFunc : function(x) {return null;};
    return {
	selectBox: selBox,
	update: (typeof (updateFunc) == 'function')?
	         updateFunc : function(x) {return null;},
	container: $(targetDiv),
	selectStr: selBox.txt,
	selectMap: {},
	show: function() {this.container.show();},
	hide: function() {this.container.hide();},
	init: function() {
	    this.oldSelect = this.selectBox.txt;
	    this.selectStr = '';
	    this.selectMap = {};
	    this.selectBox.setValue(this.selectStr);
	    this.show();
	},
	addFile: function(fname) {
	    if (this.selectMap[fname] == undefined) {
		this.selectStr += ' '+fname;
		this.selectBox.setValue(this.selectStr);
	      }
	    else {
		alert('file: '+fname+' is already selected');
	    }
	},
	accept: function() {
	    delete this.oldSelect;
	    this.hide();
	    //alert('CvK: update for: '+this.selectBox.txt)
	    this.update(this.selectBox.txt);},
	reject: function() {
	    this.selectBox.setValue(this.oldSelect);
	    delete this.oldSelect;
	    this.hide(); }

    };
};



function initialize_step1()
{
	var pars = {accessId: 'jqGetDocRoot'};
	
	cdpExec(pars, initialize_step2);
	
	return;	
};


/*
 *   After pageload initialize_step1 is called to retrieve the document root (location of pentaho-solution in the file-system).
 *   When the docroot is returned the intialize_step2 will be called with the docRoot
 */
$(initialize_step1);

function initialize_step2(result, status) {

  var docRoot = result[0];
  var cdpBase="/pentaho/content/cdp/exec?"+theCdpFile;
  /*
    prepare the file-selector for the source(s)
  */
  srcBox = createSelectBox('#srcBoxDiv');
  srcBox.setValue('*');
  srcFv = createFileViewer("#srcFileviewWindow", srcBox, updateLogTables);
  /*  initialization of buttons is performed in a call-back of the create-fileTreeRootContainer
      (see below) as the file-root first needs to be loaded. */
  srcFv.hide();
//  $('#src-sel-button')[0].onclick = function(event) { srcFv.init() };
//  $("#sfv-ok-button")[0].onclick = function(event) { srcFv.accept();};
//  $("#sfv-cancel-button")[0].onclick = function(event) { srcFv.reject();};

  /*
    prepare the file-selector for the destination(s)
  */
  dstBox = createSelectBox('#dstBoxDiv');
  dstFv = createFileViewer("#dstFileviewwindow", dstBox);
  /*  initialization of buttons is performed in a call-back of the create-fileTreeRootContainer
      (see below) as the file-root first needs to be loaded. */
  dstFv.hide();
  //  $('#dst-sel-button')[0].onclick = function(event) { dstFv.init() };
  //  $("#dfv-ok-button")[0].onclick = function(event) { dstFv.accept();};
  // $("#dfv-cancel-button")[0].onclick = function(event) { dstFv.reject();};

	    // Finish preparations of the source(s) file-viewer when root is available.
  $('#srcFileviewDiv').fileTree({root:  docRoot,
	                        script: cdpBase,
	                        skeleton: {accessId: 'genFileView'}}, 
                      function(file) { srcFv.addFile(file); });
	    // Finish preparations of the destinations(s) file-viewer when root is available.
  $('#dstFileviewDiv').fileTree({root:  docRoot,
	                        script: cdpBase,
	                        skeleton: {accessId: 'genFileView'}}, 
		     function(file) { dstFv.addFile(file);});

  /*
    Function that retrieves a destination-selection for commands
    that use a destination. For other commands the an empty string is returned.
  */
  var destPopup = $(".destPopup");
  destPopup.hide();
  
  


    /*
      update log-table and error-table
    */
    updateLogTables(srcBox.getValue());
   }

