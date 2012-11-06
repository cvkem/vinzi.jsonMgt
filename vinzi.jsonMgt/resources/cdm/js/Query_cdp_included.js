function getCdpFile (){
	// Look at the current Dashboards.context and derive the name of the
	// corresponding .cdp.xml file. The file is in the same directory/folder as
	// the dashboard and uses an identical base-name.
	    var ctx = Dashboards.context;
	    var pars = ["solution="+ctx.solution,
	                "path="+ctx.path,  
	                "file="+/([\w\d\.]*)wcdf/.exec(Dashboards.context.file)[1]+'.cdp.xml'
	                ];
	    return pars.join("&");
};

function setCdpFileAccessIds () {
    var base="/pentaho/content/cdp/get-accessids";
  var jqXhr = $.post(base, {}, function (data, status) {
	alert('received status='+status+'\n and data='+data);
  	theCdpAccessIds = eval('('+data+')');
  	return;})
  	// add an error-handler to the deferred object
  	.error(function(xhr) { 
  		if (! (acceptError == true)) {
  		  var errWin = $('#errorWindow');
            errWin.data('errorMsg', xhr.responseText);
            errWin.dialog('open');
  		};
  	return;
  	};

function setQueryWithCdp () {

	// fill the list with available accessIds
	setCdpFileAccessIds();
	
	// set a global parameter that points to the cdp.xml file
	theCdpFile = setCdpFile();
	
	//Ctors:
	// Query(queryString) --> DEPRECATED
	// Query(queryDefinition{path, dataAccessId})
	// Query(path, dataAccessId)
Query = function() {

  // Constants, or what passes for them... Pretty please leave these alone.
  var CDA_PATH = webAppPath + "/content/cda/doQuery?";
  var LEGACY_QUERY_PATH = webAppPath + "/ViewAction?solution=system&path=pentaho-cdf/actions&action=jtable.xaction";

  alert('Replaced Query, start refactoring');
  /*
     * Private fields
     */

  // Datasource type definition
  var _mode = 'CDA';
  // CDA uses file+id, Legacy uses a raw query
  var _file = '';
  var _id = '';
  var _query = '';
  // Callback for the data handler
  var _callback = null;
  // Result caching
  var _lastResultSet = null;
  // Paging and sorting
  var _page = 0;
  var _pageSize = 0;
  var _sortBy = "";
  // Exporting support
  var _exportIframe = null;

  var _params = [];
  /*
     * Initialization code
     */

  //
  (function(args){
    switch (args.length) {
      case 1:
        var cd = args[0];
        if (typeof cd.query != 'undefined') {
          // got a valid legacy cd object
          _mode = 'Legacy';
          _query = args[0];
        } else if (typeof cd.path != 'undefined' && typeof cd.dataAccessId != 'undefined'){
          // CDA-style cd object
          _mode = 'CDA';
          _file = cd.path;
          _id = cd.dataAccessId;
          if (typeof cd.sortBy == 'string' && cd.sortBy.match("^(?:[0-9]+[adAD]?,?)*$")) {
            _sortBy = cd.sortBy;
          }
          if(cd.pageSize != null){
            _pageSize = cd.pageSize;
          }
        } else {
          throw 'InvalidQuery';
        }
        break;
      case 2:
        _mode = 'CDA';
        var file = args[0];
        var id = args[1];
        if (typeof file != 'string' || typeof id != 'string') {
          throw 'InvalidQuery';
        } else {
          // Seems like we have valid parameters
          _id = id;
          _file = file;
        }
        break;
      default:
        throw "InvalidQuery";
    } 
  }(arguments));
  /*
     * Private methods
     */

  var doQuery = function(outsideCallback){
    if (typeof _callback != 'function') {
      throw 'QueryNotInitialized';
    }
    var url;
    var queryDefinition; 
    var callback = (outsideCallback ? outsideCallback : _callback);
    if (_mode == 'CDA') {
      url = CDA_PATH;
      queryDefinition = buildQueryDefinition();
    // Assemble parameters
    } else if (_mode == 'Legacy') {
      queryDefinition = _query;
      url = LEGACY_QUERY_PATH;
    }
    $.post(url, queryDefinition, function(json) {
      if(_mode == 'Legacy'){
        json = eval("(" + json + ")");
      }
      _lastResultSet = json;
      var clone = Dashboards.safeClone(true,{},_lastResultSet);
      
      if (_mode == 'Legacy') {
      	var newMetadata = [{"colIndex":0,"colType":"String","colName":"Name"}];
      	for (var i = 0 ; i < clone.metadata.length; i++) {
      		var x = i;
			newMetadata.push({"colIndex":x+1,"colType":"String","colName":clone.metadata[x]});
		}      
		clone.resultset = clone.values;
		clone.metadata = newMetadata;
		clone.values = null;
      }
      
      callback(clone);
    });
  };

  function buildQueryDefinition(overrides) {
    overrides = overrides || {};
    var queryDefinition = {};
    
    var p = Dashboards.objectToPropertiesArray( Dashboards.safeClone({},Dashboards.propertiesArrayToObject(_params), overrides) )

    for (var param in p) {
      if(p.hasOwnProperty(param)) {
        var value; 
        var name = p[param][0];
        value = Dashboards.getParameterValue(p[param][1]);
        if($.isArray(value) && value.length == 1 && ('' + value[0]).indexOf(';') >= 0){
          //special case where single element will wrongly be treated as a parseable array by cda
          value = doCsvQuoting(value[0],';');
        }
        //else will not be correctly handled for functions that return arrays
        if (typeof value == 'function') value = value();
        queryDefinition['param' + name] = value;
      }
    }
    queryDefinition.path = _file;
    queryDefinition.dataAccessId = _id;
    queryDefinition.pageSize = _pageSize;
    queryDefinition.pageStart = _page;
    queryDefinition.sortBy = _sortBy;
    return queryDefinition;
  };

  /*
     * Public interface
     */

  // Entry point

  this.exportData = function(outputType, overrides,options) {
    if (_mode != 'CDA') {
      throw "UnsupportedOperation";
    }
    if (!options) {
      options = {};
    }
    var queryDefinition = buildQueryDefinition(overrides);
    queryDefinition.outputType = outputType;
    if (outputType == 'csv' && options.separator) {
      queryDefinition.settingcsvSeparator = options.separator;
    }
    if (options.filename) {
      queryDefinition.settingattachmentName= options.filename ;
    }
    _exportIframe = _exportIframe || $('<iframe style="display:none">');
    _exportIframe.detach();
    _exportIframe[0].src = CDA_PATH + $.param(queryDefinition);
    _exportIframe.appendTo($('body'));
  }

  this.fetchData = function(params, callback) {
    switch(arguments.length) {
      case 0:
        if(_params && _callback) {
          return doQuery();
        }
        break;
      case 1:
        if (typeof arguments[0] == "function"){
          /* If we're receiving _only_ the callback, we're not
           * going to change the internal callback
           */
          return doQuery(arguments[0]);
        } else if( arguments[0] instanceof Array){
          _params = arguments[0];
          return doQuery();
        }
        break;
      case 2:
      default:
        /* We're just going to discard anything over two params */
        _params = params;
        _callback = callback;
        return doQuery();
    }
    /* If we haven't hit a return by this time,
       * the user gave us some wrong input
       */
    throw "InvalidInput";
  };

  // Result caching
  this.lastResults = function(){
    if (_lastResultSet !== null) {
      return Dashboards.safeClone(true,{},_lastResultSet);
    } else {
      throw "NoCachedResults";
    }
  };

  this.reprocessLastResults = function(outerCallback){
    if (_lastResultSet !== null) {
      var clone = Dashboards.safeClone(true,{},_lastResultSet);
      var callback = outerCallback || _callback;
      return callback(clone);
    } else {
      throw "NoCachedResults";
    }
  };

  this.reprocessResults = function(outerCallback) {
    if (_lastResultSet !== null) {
      var clone = Dashboards.safeClone(true,{},_lastResultSet);
      var callback = (outsideCallback ? outsideCallback : _callback);
      callback(_mode == 'CDA' ? clone : clone.values);
    } else {
      throw "NoCachedResults";
    }
  };

  /* Sorting
     *
     * CDA expects an array of terms consisting of a number and a letter
     * that's either 'A' or 'D'. Each term denotes, in order, a column
     * number and sort direction: 0A would then be sorting the first column
     * ascending, and 1D would sort the second column in descending order.
     * This function accepts either an array with the search terms, or
     * a comma-separated string with the terms:  "0A,1D" would then mean
     * the same as the array ["0A","1D"], which would sort the results
     * first by the first column (ascending), and then by the second
     * column (descending).
     */
  this.setSortBy = function(sortBy) {
    var newSort;
    if (sortBy === null || sortBy === undefined || sortBy === '') {
      newSort = '';
    }
    /* If we have a string as input, we need to split it into
       * an array of sort terms. Also, independently of the parameter
       * type, we need to convert everything to upper case, since want
       * to accept 'a' and 'd' even though CDA demands capitals.
       */
    else if (typeof sortBy == "string") {
      /* Valid sortBy Strings are column numbers, optionally
         *succeeded by A or D (ascending or descending), and separated by commas
         */
      if (!sortBy.match("^(?:[0-9]+[adAD]?,?)*$")) {
        throw "InvalidSortExpression";
      }
      /* Break the string into its constituent terms, filter out empty terms, if any */
      newSort = sortBy.toUpperCase().split(',').filter(function(e){
        return e !== "";
      });
    } else if (sortBy instanceof Array) {
      newSort = sortBy.map(function(d){
        return d.toUpperCase();
      });
      /* We also need to validate that each individual term is valid*/
      var invalidEntries = newSort.filter(function(e){
        return !e.match("^[0-9]+[adAD]?,?$")
      });
      if ( invalidEntries.length > 0) {
        throw "InvalidSortExpression";
      }
    }
      
    /* We check whether the parameter is the same as before,
       * and notify the caller on whether it changed
       */
    var same;
    if (newSort instanceof Array) {
      same = newSort.length != _sortBy.length;
      $.each(newSort,function(i,d){
        same = (same && d == _sortBy[i]);
        if(!same) {
          return false;
        }
      });
    } else {
      same = (newSort === _sortBy);
    }
    _sortBy = newSort;
    return !same;
  };

  this.sortBy = function(sortBy,outsideCallback) {
    /* If the parameter is not the same, and we have a valid state,
       * we can fire the query.
       */
    var changed = this.setSortBy(sortBy);
    if (!changed) {
      return false;
    } else if (_callback !== null) {
      return doQuery(outsideCallback);
    }
  };

  this.setParameters = function (params) {
    if((params instanceof Array)) {
      _params = params;
    } else {
      throw "InvalidParameters";
    }
  };

  this.setCallback = function(callback) {
    if(typeof callback == "function") {
      _callback = callback;
    } else {
      throw "InvalidCallback";
    }
  };
  /* Pagination
     *
     * We paginate by having an initial position (_page) and page size (_pageSize)
     * Paginating consists of incrementing/decrementing the initial position by the page size
     * All paging operations change the paging cursor.
     */

  // Gets the next _pageSize results
  this.nextPage = function(outsideCallback) {
    if (_pageSize > 0) {
      _page += _pageSize;
      return doQuery(outsideCallback);
    } else {
      throw "InvalidPageSize";
    }
  };

  // Gets the previous _pageSize results
  this.prevPage = function(outsideCallback) {
    if (_page > _pageSize) {
      _page -= _pageSize;
      return doQuery(outsideCallback);
    } else if (_pageSize > 0) {
      _page = 0;
      return doQuery(outsideCallback);
    } else {
      throw "AtBeggining";
    }
  };

  // Gets the page-th set of _pageSize results (0-indexed)
  this.getPage = function(page, outsideCallback) {
    if (page * _pageSize == _page) {
      return false;
    } else if (typeof page == 'number' && page >= 0) {
      _page = page * _pageSize;
      return doQuery(outsideCallback);
    } else {
      throw "InvalidPage";
    }
  };

  // Gets _pageSize results starting at page
  this.setPageStartingAt = function(page) {
    if (page == _page) {
      return false;
    } else if (typeof page == 'number' && page >= 0) {
      _page = page;
    } else {
      throw "InvalidPage";
    }
  };

  this.pageStartingAt = function(page,outsideCallback) {
    if(this.setPageStartingAt(page)) {
      return doQuery(outsideCallback);
    } else {
      return false;
    }
  };

  // Sets the page size
  this.setPageSize = function(pageSize) {
    if (typeof pageSize == 'number' && pageSize > 0) {
      _pageSize = pageSize;
    } else {
      throw "InvalidPageSize";
    }
  };

  // sets _pageSize to pageSize, and gets the first page of results
  this.initPage = function(pageSize,outsideCallback) {
    if (pageSize == _pageSize && _page == 0) {
      return false;
    } else if (typeof pageSize == 'number' && pageSize > 0) {
      _page = 0;
      _pageSize = pageSize;
      return doQuery(outsideCallback);
    } else {
      throw "InvalidPageSize";
    }
  };
};
return;   // function setQueryWithCdp
};
