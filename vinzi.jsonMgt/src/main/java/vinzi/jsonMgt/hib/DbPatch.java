package vinzi.jsonMgt.hib;

public class DbPatch {
    public Long id;
    public Long getId() { return id;}
    protected void setId(Long id)  {this.id = id; };


    public Long track_id;
    public Long getTrack_id() { return track_id;}
    public void setTrack_id(Long track_id)  {this.track_id = track_id; };


    public java.util.Date datetime;
    public java.util.Date getDatetime() { return datetime;}
    public void setDatetime(java.util.Date datetime)  {this.datetime = datetime; };


    public String path;
    public String getPath() { return path;}
    public void setPath(String path)  {this.path = path; };


    public String action;
    public String getAction() { return action;}
    public void setAction(String action)  {this.action = action; };


    public String patchkey;
    public String getPatchkey() { return patchkey;}
    public void setPatchkey(String patchkey)  {this.patchkey = patchkey; };


    public String value;
    public String getValue() { return value;}
    public void setValue(String value)  {this.value = value; };


    public DbPatch() {};

    public DbPatch(Long id, Long track_id, java.util.Date datetime, String path, String action, String patchkey, String value) {
	this.id = id;
	this.track_id = track_id;
	this.datetime = datetime;
	this.path = path;
	this.action = action;
	this.patchkey = patchkey;
	this.value = value;
	return;};

   public String toString() {
        return " id=" + this.id + " track_id=" + this.track_id + " datetime=" + this.datetime + " path=" + this.path + " action=" + this.action + " patchkey=" + this.patchkey + " value=" + this.value;};

};
