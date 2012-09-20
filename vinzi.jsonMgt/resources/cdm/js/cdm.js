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


function cdpExec(pars, updateFunc) {
    if ((typeof theCdpFile !== 'string') || (theCdpFile.length == 0))
        console.log("expected to receive string 'cdpFile'. Received: "+theCdpFile);
    var base="/pentaho/content/cdp/exec?"+theCdpFile;
    
    alert ('Going to call  CDP with parameters: '+ strObj(pars));
    
    $.post(base, pars, updateFunc);
    
    return;
};

/*
  Below are the interface functions that collect the parameters and
  call the cdP-engine for handling the actual data-processing.
  (implementation of the dashboard buttons)
*/

function createFunc () 
{
    var pars = {accessId: 'process-jsonMgt-command',
                  action: 'create',
                  source: selectedSrc,
                  destinations: "",
                  msg: ""  };
                                            
    cdpExec(pars, function (d, s) {
                                    Dashboards.fireChange('refreshTables', refreshTables+1);
                                    alert('executed a create-track for src= '+selectedSrc+
                                    ' received d='+strObj(d)+' and s='+strObj(s));
                                });

   return;
};


function helpFunc () 
{
    var pars = {accessId: 'process-jsonMgt-command',
                  action: "help",
                  source: "",
                  destinations: "",
                  msg: ""  };
                                            
    cdpExec(pars, function (d, s) {
                                    Dashboards.fireChange('refreshTables', refreshTables+1);
                                    alert('Executed a help '+
                                    ' received d='+strObj(d)+' and s='+strObj(s));
                                });

   return;
}



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



function updateTable(tblSel, srcSel, targetDiv) {
  $.post('/cdm/showTable/'+tblSel, 
	 { src: srcSel,
		 dst: null}, 
	 function(data) {
	  $(targetDiv).html(data);
	      });
};

function updateLogTables(srcSel) {
  /*
    update log-table and error-table
   */
  updateTable('actions', srcSel, '#actionDiv');
  updateTable('errors', srcSel, '#errorDiv');
};

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
    var updateFunc1 = (typeof (updateFunc) == 'function')?
	updateFunc : function(x) {return null;};
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

var srcBox, srcFv, dstBox, dstFv;

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
  srcBox = createSelectBox('#srcBox');
  srcBox.setValue('*');
  srcFv = createFileViewer("#srcFileviewWindow", srcBox, updateLogTables);
  /*  initialization of buttons is performed in a call-back of the create-fileTreeRootContainer
      (see below) as the file-root first needs to be loaded. */
 // srcFv.hide();
//  $('#src-sel-button')[0].onclick = function(event) { srcFv.init() };
//  $("#sfv-ok-button")[0].onclick = function(event) { srcFv.accept();};
//  $("#sfv-cancel-button")[0].onclick = function(event) { srcFv.reject();};

  /*
    prepare the file-selector for the destination(s)
  */
  dstBox = createSelectBox('#dstBox');
  dstFv = createFileViewer("#dstFileviewerwindow", dstBox);
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
  var partialCommand = null;  // used to store command that still 
                              // needs a destination value.
  var processCommand = function (dest, msg) {
      if (partialCommand == null) {
	  alert(" STRANGE!! no command available");
	  return;
      }
      partialCommand.dst = dest;
      partialCommand.msg = msg;

      $.post('/cdm/jsonMgt', partialCommand, 
	    function(response) {
		alert(response);
		//		ready = true;
	     });
      partialCommand = null;
      return;
  };
  
  
  var getMessageProcessCommand = function (dest) {
      processCommand(dest, "empty")
  };
  $("#dfv-ok-button")[0].onclick = function(event) { 
      dstFv.accept();
      processCommand(dstBox.getValue());
      destPopup.hide();
      return;};
  $("#dfv-cancel-button")[0].onclick = function(event) { 
      dstFv.reject();
      partialCommand = null;  // operation cancelled
      destPopup.hide();
      return;
  };
  


  /*
    Connect the jsonMgt button and pass the current selections when the button is pressed.
   */
  $('.jsonMgt').each(function() {
    var current = this;
    var theId = $(this).attr('id');
    this.onclick = function(event) {
	//	var ready = false;
	partialCommand = {action: theId,
			  src: srcBox.getValue()};
	if (   (theId == "apply") 
	    || (theId == "clean-copy")) {
	    destPopup.show();
	    dstFv.init();
	    // command will be processes as by the 
	    // dfv-ok-button or the dfv-cancel-button
	} else if (   (theId == "create")
		   || (theId == "commit"))
	    getMessageProcessCommand();
	else processCommand("", null);  // no destination and message needed

	return;
      } // end onclick
    }); // end .each

    /*
      update log-table and error-table
    */
    updateLogTables(srcBox.getValue());
   }

