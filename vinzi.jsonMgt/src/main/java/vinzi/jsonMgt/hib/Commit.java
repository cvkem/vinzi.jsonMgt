package vinzi.jsonMgt.hib;

public class Commit {
    public Long id;
    public Long getId() { return id;}
    protected void setId(Long id)  {this.id = id; };


    public Long track_id;
    public Long getTrack_id() { return track_id;}
    public void setTrack_id(Long track_id)  {this.track_id = track_id; };


    public java.util.Date datetime;
    public java.util.Date getDatetime() { return datetime;}
    public void setDatetime(java.util.Date datetime)  {this.datetime = datetime; };


    public String contents;
    public String getContents() { return contents;}
    public void setContents(String contents)  {this.contents = contents; };


    public Commit() {};

    public Commit(Long id, Long track_id, java.util.Date datetime, String contents) {
	this.id = id;
	this.track_id = track_id;
	this.datetime = datetime;
	this.contents = contents;
	return;};

   public String toString() {
        return " id=" + this.id + " track_id=" + this.track_id + " datetime=" + this.datetime + " contents=" + this.contents;};

};
