/*
  Clojure for asynchronous retrieval of file-root.
  This is a closure that requests the file-root and performs retries as long as the 
  server returns an empty string.
  The getFileTreeRoot does a check whether the value is available.
 */
function createFileTreeRootContainer (initFileTreeUsers) {
    //  The root of the file-system
    var theFileTreeRoot = null;
    var ftrCont = {
	setFileTreeRoot: function () {
	    /*
	      Function to retrieve the FileTreeRoot from the server.
	      The response should be plain format text.
	    */
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





$(function() {

  /*
    prepare the file-selector for the source(s)
  */
  var srcBox = createSelectBox('#srcBox');
  srcBox.setValue('*');
  var srcFv = createFileViewer("#srcFileviewerwindow", srcBox, updateLogTables);
  /*  initialization of buttons is performed in a call-back of the create-fileTreeRootContainer
      (see below) as the file-root first needs to be loaded. */
  srcFv.hide();
  $('#src-sel-button')[0].onclick = function(event) { srcFv.init() };
  $("#sfv-ok-button")[0].onclick = function(event) { srcFv.accept();};
  $("#sfv-cancel-button")[0].onclick = function(event) { srcFv.reject();};

  /*
    prepare the file-selector for the destination(s)
  */
  var dstBox = createSelectBox('#dstBox');
  var dstFv = createFileViewer("#dstFileviewerwindow", dstBox);
  /*  initialization of buttons is performed in a call-back of the create-fileTreeRootContainer
      (see below) as the file-root first needs to be loaded. */
  dstFv.hide();
  //  $('#dst-sel-button')[0].onclick = function(event) { dstFv.init() };
  //  $("#dfv-ok-button")[0].onclick = function(event) { dstFv.accept();};
  // $("#dfv-cancel-button")[0].onclick = function(event) { dstFv.reject();};

  var ftr = createFileTreeRootContainer(
        function () {
	    // Finish preparations of the source(s) file-viewer when root is available.
	    $('#srcFileviewer').fileTree({root:  ftr.getFileTreeRoot(),
	                        script: '/cdm/jqFileView'}, 
                      function(file) { srcFv.addFile(file); });
	    // Finish preparations of the destinations(s) file-viewer when root is available.
	    $('#dstFileviewer').fileTree({root:   ftr.getFileTreeRoot(),
	                        script: '/cdm/jqFileView'}, 
		function(file) { dstFv.addFile(file);});
	    return;
        });

  /*
    Function that retrieves a destionation-selection for commands
    that use a destionation. For other commands the an empty string is returned.
  */
  var destPopup = $(".destPopup");
  destPopup.hide();
  var partialCommand = null;  // used to store command that still 
                              // needs a destionation value.
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
   });

