import UIKit
import GoogleSignIn

@objc(GooglePlus)
class GooglePlus: CDVPlugin, GIDSignInDelegate, GIDSignInUIDelegate {
    var commandCallback: String?
    
    /**** SignIn SDK ****/
    @objc(signIn:dismissViewController:)
    func sign(_ signIn: GIDSignIn!, dismiss viewController: UIViewController!) {
        self.viewController.dismiss(animated: true, completion: nil)
    }
    
    @objc(signIn:presentViewController:)
    func sign(_ signIn: GIDSignIn!, present viewController: UIViewController!) {
        self.viewController.present(viewController, animated: true, completion: nil)
    }
    
   
    func sign(_ signIn: GIDSignIn!, didDisconnectWith user: GIDGoogleUser!,
              withError error: Error!) {
        self.sendError("User disconected")
    }
    
    
    func sign(_ signIn: GIDSignIn!, didSignInFor user: GIDGoogleUser!,
              withError error: Error!) {
        if let error = error {
            self.sendError("\(error.localizedDescription)")
        } else {
            var message: Dictionary<String, Any> = [
                "expires": user.authentication.accessTokenExpirationDate.timeIntervalSince1970,
                "serverAuthCode": user.serverAuthCode,
                "displayName": user.profile.name,
                "givenName": user.profile.givenName,
                "familyName": user.profile.familyName,
                "email": user.profile.email
            ]
            
            // If have, add the user profile picture to the result.
            if (user.profile.hasImage) {
                message["imageUrl"] = user.profile.imageURL(withDimension: 120)?.absoluteString
            }
            
            self.send(message)
        }
    }
    /**** END ****/
    
    
    @objc(login:)
    func login (command: CDVInvokedUrlCommand) {
        self.getGIDSignInObject(command).signIn()
    }
    
    @objc(trySilentLogin:)
    func trySilentLogin (command: CDVInvokedUrlCommand) {
        self.getGIDSignInObject(command).signInSilently()
    }
    
    @objc(logout:)
    func logout (command: CDVInvokedUrlCommand) {
        GIDSignIn.sharedInstance().signOut()
        self.sendString("Logged out")
    }
    
    @objc(disconnect:)
    func disconnect (command: CDVInvokedUrlCommand) {
        GIDSignIn.sharedInstance().disconnect()
        self.sendString("Disconnected")
    }
    
    func getGIDSignInObject (_ command: CDVInvokedUrlCommand) -> GIDSignIn! {
        self.commandCallback = command.callbackId
        
        let options: [String: Any] = command.arguments.first as! [String: Any]
        
        let reversedClientId: String! = self.getReversedClientId()
        if (reversedClientId == nil) {
            self.sendError("Could not find REVERSED_CLIENT_ID url scheme in app .plist")
            return nil
        }
        
        let scopesString: String! = options["scopes"] as? String
        let serverClientId: String! = options["webClientId"] as? String
        let offline: Bool = options["offline"] as! Bool
        let accountName: String! = options["accountName"] as? String
        
        // Initialize sign-in
        let signInObj: GIDSignIn = GIDSignIn()
        signInObj.signOut()
        
        signInObj.delegate = self
        signInObj.uiDelegate = self
        
        signInObj.clientID = self.reverseUrlScheme(reversedClientId)
        
        // If webClientId is include and offline is true, set serverClientId for received serverAuthCode.
        if (serverClientId != nil && offline) {
            signInObj.serverClientID = serverClientId
        }
        
        if (scopesString != nil && !scopesString.isEmpty) {
            signInObj.scopes = scopesString.split(separator: " ").map(String.init)
        }
        
        if (accountName != nil) {
            signInObj.loginHint = accountName
        }
        
        return signInObj
    }
    
    func reverseUrlScheme (_ reversedClientId: String) -> String {
        var originalArray: Array = reversedClientId.split(separator: ".").map(String.init)
        originalArray.reverse()
        let reversedString: String = originalArray.joined(separator: ".")
        
        return reversedString
    }
    
    // Get the REVERSED_CLIENT_ID
    func getReversedClientId () -> String! {
        if let urlTypes: [Any] = Bundle.main.infoDictionary!["CFBundleURLTypes"] as? [Any] {
            for value in urlTypes {
                let dict: [String: Any] = value as! [String: Any]
                let urlName: String = dict["CFBundleURLName"] as! String
                if (urlName == "REVERSED_CLIENT_ID") {
                    if let urlSchemes: [String] = dict["CFBundleURLSchemes"] as? [String] {
                        return urlSchemes[0]
                    }
                }
            }
        }
        return nil
    }
    
    // Send result
    func send (_ message: Dictionary<String, Any>, _ status: CDVCommandStatus = CDVCommandStatus_OK) {
        if let callbackId = self.commandCallback {
            self.commandCallback = nil
            let pluginResult = CDVPluginResult(
                status: status,
                messageAs: message
            )
            self.commandDelegate!.send(
                pluginResult,
                callbackId: callbackId
            )
        }
    }
    
    func sendString (_ message: String, _ status: CDVCommandStatus = CDVCommandStatus_OK) {
        if let callbackId = self.commandCallback {
            self.commandCallback = nil
            let pluginResult = CDVPluginResult(
                status: status,
                messageAs: message
            )
            self.commandDelegate!.send(
                pluginResult,
                callbackId: callbackId
            )
        }
    }
    
    // Send error
    func sendError (_ message: String) {
        self.sendString(message, CDVCommandStatus_ERROR)
    }
}

