function refreshStatus() {
	var xmlhttp = new XMLHttpRequest();
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
		  	var incomingConnections = this.responseXML.getElementsByTagName("IncomingConnections")[0].childNodes[0].nodeValue
			var outgoingConnections = this.responseXML.getElementsByTagName("OutgoingConnections")[0].childNodes[0].nodeValue
		  	var knownHosts = this.responseXML.getElementsByTagName("KnownHosts")[0].childNodes[0].nodeValue
			var failingHosts = this.responseXML.getElementsByTagName("FailingHosts")[0].childNodes[0].nodeValue
			var hopelessHosts = this.responseXML.getElementsByTagName("HopelessHosts")[0].childNodes[0].nodeValue
			var timesBrowsed = this.responseXML.getElementsByTagName("TimesBrowsed")[0].childNodes[0].nodeValue
			
			document.getElementById("incoming-connections").textContent = incomingConnections
			document.getElementById("outgoing-connections").textContent = outgoingConnections
			document.getElementById("known-hosts").textContent = knownHosts
			document.getElementById("failing-hosts").textContent = failingHosts
			document.getElementById("hopeless-hosts").textContent = hopelessHosts
			document.getElementById("times-browsed").textContent = timesBrowsed
		}
	}
	xmlhttp.open("GET", "/MuWire/Status", true);
	xmlhttp.send();
}

function initStatus() {
	setInterval(refreshStatus, 3000);
	setTimeout(refreshStatus, 1);
}

document.addEventListener("DOMContentLoaded", function() {
   initStatus();
}, true);
