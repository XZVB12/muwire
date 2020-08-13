function refreshConnectionsCount() {
	var xmlhttp = new XMLHttpRequest();
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4) {
		  var connectionCountSpan = document.getElementById("connectionsCount");
		  var connectionIcon = document.getElementById("connectionsIcon");
		  if (this.status == 200) {
			var image;
			var connections = this.responseXML.getElementsByTagName("Connections");
			var count = connections[0].childNodes[0].nodeValue
			var countString = ""+count;
			connectionCountSpan.innerHTML = countString;
			if (count > 0)
			    image = "Connect.png";
			else
			    image = "NotStarted.png";
			connectionIcon.innerHTML = "<img src=\"images/" + image + "\" alt=\"\">";
		  } else {
			connectionCountSpan.textContent = _t("Down");
			connectionIcon.textContent = "";
		  }
		}
	}
	xmlhttp.open("GET", "/MuWire/Search?section=connectionsCount", true);
	xmlhttp.send();
}

function initConnectionsCount() {
	setInterval(refreshConnectionsCount, 3000);
	setTimeout(refreshConnectionsCount, 1);
}

document.addEventListener("DOMContentLoaded", function() {
   initConnectionsCount();
}, true);
