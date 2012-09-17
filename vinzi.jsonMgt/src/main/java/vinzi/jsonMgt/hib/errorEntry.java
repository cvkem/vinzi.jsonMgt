package vinzi.jsonMgt.hib;

public class errorEntry {
    public Long id;
    public Long getId() { return id;}
    protected void setId(Long id)  {this.id = id; };


    public java.util.Date datetime;
    public java.util.Date getDatetime() { return datetime;}
    public void setDatetime(java.util.Date datetime)  {this.datetime = datetime; };


    public String track;
    public String getTrack() { return track;}
    public void setTrack(String track)  {this.track = track; };


    public String command;
    public String getCommand() { return command;}
    public void setCommand(String command)  {this.command = command; };


    public String error;
    public String getError() { return error;}
    public void setError(String error)  {this.error = error; };


    public String d_user;
    public String getD_user() { return d_user;}
    public void setD_user(String d_user)  {this.d_user = d_user; };


    public errorEntry() {};

    public errorEntry(Long id, java.util.Date datetime, String track, String command, String error, String d_user) {
	this.id = id;
	this.datetime = datetime;
	this.track = track;
	this.command = command;
	this.error = error;
	this.d_user = d_user;
	return;};

   public String toString() {
        return " id=" + this.id + " datetime=" + this.datetime + " track=" + this.track + " command=" + this.command + " error=" + this.error + " d_user=" + this.d_user;};

};
